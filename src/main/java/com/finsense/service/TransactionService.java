package com.finsense.service;

import com.finsense.dao.CategoryDAO;
import com.finsense.dao.TransactionDAO;
import com.finsense.exception.ValidationException;
import com.finsense.model.Category;
import com.finsense.model.MonthlySummary;
import com.finsense.model.Money;
import com.finsense.model.Transaction;
import com.finsense.model.enums.TransactionType;
import com.finsense.util.SessionManager;
import com.finsense.util.ValidationUtil;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Coordinates transaction validation and summaries using constructor-injected DAOs. */
public class TransactionService {
    private final TransactionDAO transactionDAO;
    private final CategoryDAO categoryDAO;
    public TransactionService() { this(new TransactionDAO(),new CategoryDAO()); }
    public TransactionService(TransactionDAO transactionDAO,CategoryDAO categoryDAO) {
        this.transactionDAO=transactionDAO; this.categoryDAO=categoryDAO;
    }
    private long userId() { return SessionManager.getInstance().requireUserId(); }

    public List<Category> categories(TransactionType type) {
        return categoryDAO.findByType(ValidationUtil.requireSelection(type,"Type"),userId());
    }
    public List<Transaction> find(LocalDate from,LocalDate through,TransactionType type,Long categoryId,String text) {
        if (from!=null && through!=null && through.isBefore(from)) throw new ValidationException("End date cannot be before start date.");
        return transactionDAO.find(userId(),from,through,type,categoryId,text);
    }
    public Transaction add(Long categoryId,TransactionType type,String amount,LocalDate date,String description) {
        long uid=userId(); Category category=visibleCategory(categoryId,uid,type);
        Money value=ValidationUtil.parsePositiveAmount(amount,"Amount");
        LocalDate on=ValidationUtil.requireDate(date,"Date"); String note=ValidationUtil.optionalText(description,"Description",255);
        long id=transactionDAO.insert(uid,category.id(),type,value,on,note);
        return new Transaction(id,uid,category,type,value,on,note,null);
    }
    public Transaction update(long id,Long categoryId,TransactionType type,String amount,LocalDate date,String description) {
        long uid=userId();
        if(transactionDAO.findById(id,uid).isEmpty()) throw new ValidationException("Transaction not found.");
        Category category=visibleCategory(categoryId,uid,type); Money value=ValidationUtil.parsePositiveAmount(amount,"Amount");
        LocalDate on=ValidationUtil.requireDate(date,"Date"); String note=ValidationUtil.optionalText(description,"Description",255);
        transactionDAO.update(id,uid,category.id(),type,value,on,note);
        return new Transaction(id,uid,category,type,value,on,note,null);
    }
    public void delete(long id) {
        if(!transactionDAO.delete(id,userId())) throw new ValidationException("Transaction not found.");
    }
    private Category visibleCategory(Long categoryId,long uid,TransactionType type) {
        if(categoryId==null) throw new ValidationException("Please select a category.");
        Category c=categoryDAO.findVisibleById(categoryId,uid).orElseThrow(()->new ValidationException("Category not found."));
        if(c.type()!=type) throw new ValidationException("Choose a category matching the transaction type.");
        return c;
    }
    public MonthlySummary monthlySummary(YearMonth month) {
        long uid=userId(); LocalDate start=month.atDay(1),end=month.atEndOfMonth();
        Money income=Money.ofPaise(transactionDAO.sum(uid,TransactionType.INCOME,start,end,false));
        Money expense=Money.ofPaise(transactionDAO.sum(uid,TransactionType.EXPENSE,start,end,false));
        return new MonthlySummary(income,expense,income.minus(expense));
    }
    /** Breakdown uses Streams groupingBy + summingLong over exact paise values. */
    public Map<Category,Money> categoryBreakdown(YearMonth month,TransactionType type) {
        List<Transaction> rows=find(month.atDay(1),month.atEndOfMonth(),type,null,null);
        return rows.stream().collect(Collectors.groupingBy(Transaction::category,
                Collectors.summingLong(t->t.amount().getPaise())))
                .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,e->Money.ofPaise(e.getValue())));
    }
    /** Average monthly expense over the requested calendar months, excluding commitment categories. */
    public Money averageMonthlyExpenses(int months) {
        if(months<1 || months>36) throw new ValidationException("Months must be between 1 and 36.");
        YearMonth current=YearMonth.now(); LocalDate from=current.minusMonths(months-1L).atDay(1);
        long total=transactionDAO.sum(userId(),TransactionType.EXPENSE,from,current.atEndOfMonth(),true);
        return Money.ofPaise(total).divide(months);
    }
    public Money thisMonthExpenses() {
        YearMonth month=YearMonth.now();
        return Money.ofPaise(transactionDAO.sum(userId(),TransactionType.EXPENSE,month.atDay(1),LocalDate.now(),true));
    }
    /** At least one transaction is old enough to represent a full month of recorded history. */
    public boolean hasAtLeastOneFullMonthOfData() {
        LocalDate cutoff=LocalDate.now().minusDays(30);
        return !find(null,cutoff,null,null,null).isEmpty();
    }
}
