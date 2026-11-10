package com.finsense.service;

import com.finsense.dao.CategoryDAO;
import com.finsense.dao.TransactionDAO;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TransactionServiceTest {
    private DatabaseConnection db;
    private TransactionService service;
    private CategoryDAO categories;
    private User userA,userB;
    @BeforeEach void setUp() {
        db=TestDatabase.freshEmptyDatabase(); new DatabaseInitializer(db).initialize();
        UserDAO users=new UserDAO();
        long a=users.insert("txn_a","hash","salt","A"),b=users.insert("txn_b","hash","salt","B");
        userA=new User(a,"txn_a","A",LocalDateTime.now()); userB=new User(b,"txn_b","B",LocalDateTime.now());
        SessionManager.getInstance().login(userA); categories=new CategoryDAO();
        service=new TransactionService(new TransactionDAO(),categories);
    }
    @AfterEach void tearDown() { SessionManager.getInstance().logout(); db.close(); }

    @Test void monthlySummaryAndCategoryBreakdownUseExactAmounts() {
        Category dining=byName("Dining Out",TransactionType.EXPENSE),rent=byName("Rent",TransactionType.EXPENSE),
                salary=byName("Salary",TransactionType.INCOME); YearMonth m=YearMonth.now();
        service.add(dining.id(),TransactionType.EXPENSE,"1200",m.atDay(2),"lunch");
        service.add(rent.id(),TransactionType.EXPENSE,"800",m.atDay(3),"rent");
        service.add(salary.id(),TransactionType.INCOME,"5000",m.atDay(1),"salary");
        var summary=service.monthlySummary(m);
        assertEquals(Money.ofRupees(5000),summary.income()); assertEquals(Money.ofRupees(2000),summary.expenses());
        assertEquals(Money.ofRupees(3000),summary.net());
        Map<Category,Money> breakdown=service.categoryBreakdown(m,TransactionType.EXPENSE);
        assertEquals(Money.ofRupees(1200),breakdown.get(dining)); assertEquals(Money.ofRupees(800),breakdown.get(rent));
    }
    @Test void monthlyExpenseAverageExcludesCommitmentCategories() {
        Category emi=byName("EMI / Loan Repayment",TransactionType.EXPENSE),groceries=byName("Groceries",TransactionType.EXPENSE);
        LocalDate today=LocalDate.now();
        service.add(emi.id(),TransactionType.EXPENSE,"9000",today,"loan installment");
        service.add(groceries.id(),TransactionType.EXPENSE,"3000",today,"food");
        assertEquals(Money.ofRupees(1000),service.averageMonthlyExpenses(3));
        assertEquals(Money.ofRupees(3000),service.thisMonthExpenses());
    }
    @Test void anotherUserCannotSeeTransactions() {
        Category food=byName("Groceries",TransactionType.EXPENSE); service.add(food.id(),TransactionType.EXPENSE,"500",LocalDate.now(),"private");
        SessionManager.getInstance().login(userB);
        assertTrue(service.find(null,null,null,null,null).isEmpty());
    }
    private Category byName(String name,TransactionType type) { return service.categories(type).stream().filter(c->c.name().equals(name)).findFirst().orElseThrow(); }
}
