package com.finsense.service;

import com.finsense.model.Category;
import com.finsense.model.CategorySpending;
import com.finsense.model.InsightReport;
import com.finsense.model.MonthlyMetrics;
import com.finsense.model.MonthlySummary;
import com.finsense.model.Money;
import com.finsense.model.enums.TransactionType;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Rule-based insight calculations over transaction aggregates and behaviour history. */
public class InsightService {
    public static final int COMPARISON_MONTHS=6,TOP_CATEGORY_COUNT=5,SUGGESTION_INCREASE_THRESHOLD_PERCENT=20;
    private final TransactionService transactions;private final BehaviourService behaviour;
    public InsightService(){this(new TransactionService(),new BehaviourService());}
    public InsightService(TransactionService transactions,BehaviourService behaviour){this.transactions=transactions;this.behaviour=behaviour;}
    public InsightReport report(YearMonth month){
        List<MonthlyMetrics> comparison=new ArrayList<>();
        for(int offset=COMPARISON_MONTHS-1;offset>=0;offset--){YearMonth m=month.minusMonths(offset);MonthlySummary s=transactions.monthlySummary(m);comparison.add(new MonthlyMetrics(m,s.income(),s.expenses()));}
        Map<Category,Money> current=transactions.categoryBreakdown(month,TransactionType.EXPENSE);
        Map<Category,Money> previous=transactions.categoryBreakdown(month.minusMonths(1),TransactionType.EXPENSE);
        List<CategorySpending> categories=current.entrySet().stream().map(entry->{
            Money prior=previous.getOrDefault(entry.getKey(),Money.ZERO);
            return new CategorySpending(entry.getKey().name(),entry.getValue(),prior,monthOverMonthPercent(entry.getValue(),prior),entry.getKey().nature());
        }).sorted(Comparator.comparingLong((CategorySpending value)->value.current().getPaise()).reversed()).toList();
        Money essential=Money.ZERO,discretionary=Money.ZERO;
        for(var entry:current.entrySet()){
            if("DISCRETIONARY".equals(entry.getKey().nature()))discretionary=discretionary.plus(entry.getValue());
            else essential=essential.plus(entry.getValue());
        }
        MonthlySummary currentSummary=transactions.monthlySummary(month);
        int savingsRate=currentSummary.income().isZero()?0:(int)Math.round(currentSummary.net().ratioTo(currentSummary.income())*100.0);
        List<String> suggestions=suggestions(categories);
        return new InsightReport(comparison,categories.stream().limit(TOP_CATEGORY_COUNT).toList(),essential,discretionary,
                savingsRate,suggestions,behaviour.history());
    }
    /** A newly appearing nonzero category is reported as +100%, avoiding division by zero. */
    public static int monthOverMonthPercent(Money current,Money previous){
        if(previous.isZero())return current.isZero()?0:100;
        return (int)Math.round((current.ratioTo(previous)-1.0)*100.0);
    }
    private static List<String> suggestions(List<CategorySpending> categories){
        List<String> result=new ArrayList<>();
        for(CategorySpending category:categories){
            if(category.previous().isZero() && category.current().isPositive())result.add("New spending in "+category.categoryName()+": "+category.current().format()+".");
            else if(category.changePercent()>=SUGGESTION_INCREASE_THRESHOLD_PERCENT)
                result.add(category.categoryName()+" is up "+category.changePercent()+"% compared with last month.");
            if(result.size()==TOP_CATEGORY_COUNT)break;
        }
        if(result.isEmpty())result.add("Your spending is steady across tracked categories.");
        return List.copyOf(result);
    }
}
