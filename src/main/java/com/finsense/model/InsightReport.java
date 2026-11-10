package com.finsense.model;

import java.util.List;
/** Read-only personalized insight data assembled for one month. */
public record InsightReport(List<MonthlyMetrics> monthlyComparison,List<CategorySpending> topCategories,
        Money essentialExpenses,Money discretionaryExpenses,int savingsRatePercent,List<String> suggestions,
        List<BehaviourProfile> behaviourHistory) {
    public InsightReport { monthlyComparison=List.copyOf(monthlyComparison);topCategories=List.copyOf(topCategories);
        suggestions=List.copyOf(suggestions);behaviourHistory=List.copyOf(behaviourHistory); }
}
