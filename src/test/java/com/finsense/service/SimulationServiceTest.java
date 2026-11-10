package com.finsense.service;

import com.finsense.dao.FinancialProfileDAO;
import com.finsense.dao.UserDAO;
import com.finsense.database.DatabaseConnection;
import com.finsense.database.DatabaseInitializer;
import com.finsense.database.TestDatabase;
import com.finsense.decision.PurchaseRequest;
import com.finsense.model.Category;
import com.finsense.model.Commitment;
import com.finsense.model.Money;
import com.finsense.model.SavingsGoal;
import com.finsense.model.enums.CommitmentType;
import com.finsense.model.enums.GoalPriority;
import com.finsense.model.enums.GoalStatus;
import com.finsense.model.enums.Necessity;
import com.finsense.model.enums.TransactionType;
import com.finsense.model.FinancialProfile;
import com.finsense.model.User;
import com.finsense.util.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SimulationServiceTest {
    private DatabaseConnection db;private SimulationService service;private long userId;
    @BeforeEach void setUp(){
        db=TestDatabase.freshEmptyDatabase();new DatabaseInitializer(db).initialize();
        userId=new UserDAO().insert("simulation_user","hash","salt","Simulation User");
        SessionManager.getInstance().login(new User(userId,"simulation_user","Simulation User",LocalDateTime.now()));
        new FinancialProfileDAO().upsert(new FinancialProfile(userId,Money.ofRupees(100000),Money.ofRupees(20000),Money.ZERO,
                Money.ofRupees(150000),Money.ofRupees(10000),null));
        service=new SimulationService();
    }
    @AfterEach void tearDown(){SessionManager.getInstance().logout();db.close();}
    @Test void simulationDoesNotChangeTransactionPurchaseOrHistoryCounts() throws Exception{
        Category category=new com.finsense.dao.CategoryDAO().findByType(TransactionType.EXPENSE,userId).stream().filter(c->c.name().equals("Electronics")).findFirst().orElseThrow();
        long txns=count("transactions"),purchases=count("purchases"),history=count("purchase_history");
        var result=service.simulate(new PurchaseRequest("Phone",category,Money.ofRupees(25000),Necessity.WANT,null),1);
        assertEquals(SimulationService.PROJECTION_MONTHS,result.projection().size());
        assertEquals(txns,count("transactions"));assertEquals(purchases,count("purchases"));assertEquals(history,count("purchase_history"));
    }
    @Test void commitmentEndingInMonthFourRaisesProjectionFromThatMonth(){
        LocalDate asOf=LocalDate.of(2026,1,1);
        Commitment emi=new Commitment(1L,userId,"Car EMI",CommitmentType.EMI,Money.ofRupees(5000),asOf.minusMonths(10),LocalDate.of(2026,4,15));
        var projection=SimulationService.project(Money.ofRupees(100000),Money.ofRupees(1000),Money.ofRupees(20000),
                Money.ofRupees(12000),List.of(emi),asOf,1,12);
        assertEquals(Money.ofRupees(109000),projection.get(3).baselineSavings());
        assertEquals(Money.ofRupees(25000),projection.get(3).baselineDisposable());
    }
    @Test void hypotheticalPurchasePushesGoalEtaBack(){
        LocalDate asOf=LocalDate.of(2026,1,1);
        SavingsGoal goal=new SavingsGoal(1L,userId,"Holiday",Money.ofRupees(20000),LocalDate.of(2026,6,1),GoalPriority.HIGH,GoalStatus.ACTIVE,Money.ZERO);
        var impact=SimulationService.goalImpacts(List.of(goal),Money.ofRupees(5000),Money.ofRupees(10000),asOf).get(0);
        assertEquals(LocalDate.of(2026,5,1),impact.baselineEta());assertEquals(LocalDate.of(2026,7,1),impact.purchaseEta());
        assertEquals(2,impact.delayMonths());assertTrue(impact.missesDeadline());
    }
    private long count(String table)throws Exception{
        try(Statement s=db.getConnection().createStatement();ResultSet rs=s.executeQuery("SELECT COUNT(*) FROM "+table)){rs.next();return rs.getLong(1);}
    }
}
