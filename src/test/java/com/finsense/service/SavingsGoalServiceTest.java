package com.finsense.service;

import com.finsense.dao.FinancialProfileDAO;
import com.finsense.dao.SavingsGoalDAO;
import com.finsense.dao.UserDAO;
import com.finsense.database.DatabaseConnection;
import com.finsense.database.DatabaseInitializer;
import com.finsense.database.TestDatabase;
import com.finsense.exception.ValidationException;
import com.finsense.model.FinancialProfile;
import com.finsense.model.Money;
import com.finsense.model.SavingsGoal;
import com.finsense.model.User;
import com.finsense.model.enums.GoalPriority;
import com.finsense.model.enums.GoalStatus;
import com.finsense.util.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class SavingsGoalServiceTest {
    private DatabaseConnection db; private SavingsGoalService service; private FinancialProfileDAO profiles;
    @BeforeEach void setUp() {
        db=TestDatabase.freshEmptyDatabase();new DatabaseInitializer(db).initialize();
        long uid=new UserDAO().insert("goal_user","hash","salt","Goal User");
        SessionManager.getInstance().login(new User(uid,"goal_user","Goal User",LocalDateTime.now()));
        profiles=new FinancialProfileDAO(); profiles.upsert(new FinancialProfile(uid,Money.ofRupees(50000),Money.ZERO,Money.ZERO,
                Money.ofRupees(10000),Money.ZERO,null));
        service=new SavingsGoalService(new SavingsGoalDAO(),profiles);
    }
    @AfterEach void tearDown(){SessionManager.getInstance().logout();db.close();}

    @Test void progressPercentageUsesDerivedSavedAmount(){
        SavingsGoal g=new SavingsGoal(1L,1,"Trip",Money.ofRupees(2000),LocalDate.now().plusMonths(1),GoalPriority.MEDIUM,GoalStatus.ACTIVE,Money.ofRupees(500));
        assertEquals(25.0,g.progressPercent()); assertEquals(Money.ofRupees(1500),g.remainingAmount());
    }
    @Test void contributionReservesFreeSavingsWithoutChangingTotalSavings(){
        SavingsGoal g=service.create("Trip","5000",LocalDate.now().plusMonths(3),GoalPriority.HIGH);
        assertEquals(Money.ofRupees(10000),service.freeSavings()); service.contribute(g.id(),"2000",LocalDate.now(),"first");
        assertEquals(Money.ofRupees(8000),service.freeSavings());
        assertEquals(Money.ofRupees(10000),profiles.findByUserId(g.userId()).orElseThrow().getCurrentSavings());
        assertEquals(Money.ofRupees(2000),service.goals().get(0).savedAmount());
    }
    @Test void contributionAboveFreeSavingsIsRejectedWithAvailableAmount(){
        SavingsGoal g=service.create("Trip","50000",LocalDate.now().plusMonths(3),GoalPriority.MEDIUM);
        ValidationException ex=assertThrows(ValidationException.class,()->service.contribute(g.id(),"10001",LocalDate.now(),null));
        assertTrue(ex.getMessage().contains("free savings of ₹10,000"));
    }
    @Test void reachingTargetCompletesGoalAutomatically(){
        SavingsGoal g=service.create("Trip","2000",LocalDate.now().plusMonths(1),GoalPriority.LOW);
        SavingsGoal completed=service.contribute(g.id(),"2000",LocalDate.now(),null);
        assertEquals(GoalStatus.COMPLETED,completed.status()); assertEquals(Money.ofRupees(2000),completed.savedAmount());
    }
    @Test void cancellingGoalReleasesItsContributions(){
        SavingsGoal g=service.create("Trip","5000",LocalDate.now().plusMonths(2),GoalPriority.LOW);
        service.contribute(g.id(),"3000",LocalDate.now(),null); assertEquals(Money.ofRupees(7000),service.freeSavings());
        service.cancel(g.id()); assertEquals(Money.ofRupees(10000),service.freeSavings());
        assertEquals(GoalStatus.CANCELLED,service.goals().get(0).status());
    }
}
