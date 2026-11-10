package com.finsense.service;

import com.finsense.dao.CategoryDAO;
import com.finsense.dao.UserDAO;
import com.finsense.database.DatabaseConnection;
import com.finsense.database.DatabaseInitializer;
import com.finsense.database.TestDatabase;
import com.finsense.model.Category;
import com.finsense.model.Money;
import com.finsense.model.User;
import com.finsense.model.enums.TransactionType;
import com.finsense.util.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.YearMonth;
import static org.junit.jupiter.api.Assertions.*;

class InsightServiceTest {
    private DatabaseConnection db;private InsightService insights;private TransactionService transactions;private long userId;
    @BeforeEach void setUp(){
        db=TestDatabase.freshEmptyDatabase();new DatabaseInitializer(db).initialize();
        userId=new UserDAO().insert("insight_user","hash","salt","Insight User");
        SessionManager.getInstance().login(new User(userId,"insight_user","Insight User",LocalDateTime.now()));
        transactions=new TransactionService();insights=new InsightService(transactions,new BehaviourService());
    }
    @AfterEach void tearDown(){SessionManager.getInstance().logout();db.close();}
    @Test void calculatesSixMonthsTopCategoriesExpenseSplitAndSavingsRate(){
        YearMonth month=YearMonth.now(),previous=month.minusMonths(1);
        Category income=category("Salary",TransactionType.INCOME),rent=category("Rent",TransactionType.EXPENSE),
                dining=category("Dining Out",TransactionType.EXPENSE),groceries=category("Groceries",TransactionType.EXPENSE);
        transactions.add(income.id(),TransactionType.INCOME,"10000",month.atDay(2),"pay");
        transactions.add(rent.id(),TransactionType.EXPENSE,"2000",month.atDay(3),"rent");
        transactions.add(dining.id(),TransactionType.EXPENSE,"4000",month.atDay(4),"meal");
        transactions.add(dining.id(),TransactionType.EXPENSE,"1000",previous.atDay(4),"meal");
        transactions.add(groceries.id(),TransactionType.EXPENSE,"2000",previous.atDay(6),"food");
        var report=insights.report(month);
        assertEquals(InsightService.COMPARISON_MONTHS,report.monthlyComparison().size());
        assertEquals(Money.ofRupees(2000),report.essentialExpenses());assertEquals(Money.ofRupees(4000),report.discretionaryExpenses());
        assertEquals(40,report.savingsRatePercent());
        var diningInsight=report.topCategories().stream().filter(c->c.categoryName().equals("Dining Out")).findFirst().orElseThrow();
        assertEquals(300,diningInsight.changePercent());assertTrue(report.suggestions().stream().anyMatch(s->s.contains("Dining Out is up 300%")));
    }
    @Test void monthOverMonthChangeHandlesZeroPreviousMonth(){
        assertEquals(100,InsightService.monthOverMonthPercent(Money.ofRupees(200),Money.ZERO));
        assertEquals(0,InsightService.monthOverMonthPercent(Money.ZERO,Money.ZERO));
        assertEquals(-50,InsightService.monthOverMonthPercent(Money.ofRupees(50),Money.ofRupees(100)));
    }
    private Category category(String name,TransactionType type){
        return new CategoryDAO().findByType(type,userId).stream().filter(c->c.name().equals(name)).findFirst().orElseThrow();
    }
}
