package com.finsense.decision;

import com.finsense.decision.rules.*;
import com.finsense.exception.IncompleteProfileException;
import com.finsense.exception.ValidationException;
import com.finsense.model.Category;
import com.finsense.model.Money;
import com.finsense.model.enums.BehaviourType;
import com.finsense.model.enums.GoalPriority;
import com.finsense.model.enums.Necessity;
import com.finsense.model.enums.TransactionType;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DecisionEngineTest {
    private static final Category DISCRETIONARY=new Category(1L,null,"Electronics",TransactionType.EXPENSE,"DISCRETIONARY",false);
    private final DecisionEngine engine=DecisionEngineFactory.create();
    private static FinancialSnapshot snapshot(Money disposable,Money free,Money reserve,Money commitments,Money income,
                                               List<GoalSnapshot> goals,BehaviourType type,boolean fallback){
        return new FinancialSnapshot(income,Money.ofRupees(10000),commitments,Money.ofRupees(10000),disposable,reserve,free,
                goals,type,Money.ofRupees(10000),Money.ofRupees(10000),fallback,LocalDate.of(2026,1,1));
    }
    private static PurchaseRequest request(long price,Necessity necessity){return new PurchaseRequest("Test item",DISCRETIONARY,Money.ofRupees(price),necessity,null);}
    private static FinancialSnapshot healthy(){return snapshot(Money.ofRupees(65000),Money.ofRupees(200000),Money.ofRupees(75000),
            Money.ofRupees(5000),Money.ofRupees(100000),List.of(),BehaviourType.SMART_SHOPPER,false);}

    @Test void cheapNeedWithHealthyFinancesIsBuy(){assertEquals(Recommendation.BUY,engine.evaluate(request(5000,Necessity.NEED),healthy()).recommendation());}
    @Test void negativeDisposableWantIsCappedAndNeverBuy(){
        var s=snapshot(Money.ofRupees(-10000),Money.ofRupees(1000000),Money.ofRupees(100000),Money.ofRupees(10000),Money.ofRupees(50000),List.of(),BehaviourType.SMART_SHOPPER,false);
        assertNotEquals(Recommendation.BUY,engine.evaluate(request(1000,Necessity.WANT),s).recommendation());
    }
    @Test void priceAboveSavingsPlusDisposableIsHardStopAvoid(){
        var s=snapshot(Money.ofRupees(50000),Money.ofRupees(100000),Money.ofRupees(10000),Money.ofRupees(5000),Money.ofRupees(100000),List.of(),BehaviourType.SMART_SHOPPER,false);
        assertEquals(Recommendation.AVOID,engine.evaluate(request(150001,Necessity.NEED),s).recommendation());
    }
    @Test void purchaseThatBreaksEmergencyReserveIsWaitOrWorse(){
        var s=snapshot(Money.ofRupees(50000),Money.ofRupees(100000),Money.ofRupees(95000),Money.ofRupees(5000),Money.ofRupees(100000),List.of(),BehaviourType.SMART_SHOPPER,false);
        assertTrue(engine.evaluate(request(10000,Necessity.NEED),s).recommendation().ordinal()>=Recommendation.WAIT.ordinal());
    }
    @Test void highPriorityGoalDeadlineCapsAtConsider(){
        var goal=new GoalSnapshot(Money.ofRupees(20000),LocalDate.of(2026,2,1),GoalPriority.HIGH);
        var s=snapshot(Money.ofRupees(20000),Money.ofRupees(500000),Money.ofRupees(150000),Money.ofRupees(5000),Money.ofRupees(100000),List.of(goal),BehaviourType.SMART_SHOPPER,false);
        assertTrue(engine.evaluate(request(1,Necessity.NEED),s).recommendation().ordinal()<=Recommendation.CONSIDER.ordinal());
    }
    @Test void commitmentsAboveSixtyPercentCapAtWait(){
        var s=snapshot(Money.ofRupees(20000),Money.ofRupees(500000),Money.ofRupees(220000),Money.ofRupees(70000),Money.ofRupees(100000),List.of(),BehaviourType.SMART_SHOPPER,false);
        assertTrue(engine.evaluate(request(1000,Necessity.NEED),s).recommendation().ordinal()>=Recommendation.WAIT.ordinal());
    }
    @Test void missingProfileThrowsIncompleteProfile(){assertThrows(IncompleteProfileException.class,()->engine.evaluate(request(1,Necessity.NEED),null));}
    @Test void nonPositivePriceThrowsValidation(){assertThrows(ValidationException.class,()->engine.evaluate(request(0,Necessity.NEED),healthy()));}
    @Test void impulseWantScoresBelowSmartShopper(){
        var impulse=snapshot(Money.ofRupees(65000),Money.ofRupees(200000),Money.ofRupees(75000),Money.ofRupees(5000),Money.ofRupees(100000),List.of(),BehaviourType.IMPULSE_BUYER,false);
        assertTrue(engine.evaluate(request(5000,Necessity.WANT),impulse).score()<engine.evaluate(request(5000,Necessity.WANT),healthy()).score());
    }
    @Test void restrictiveCapWinsAndExplanationNamesIt(){
        DecisionEngine capped=new DecisionEngine(List.of((r,s)->RuleResult.points(100,Severity.INFO,"Strong positive signal."),
                (r,s)->RuleResult.cap(0,Recommendation.WAIT,Severity.CRITICAL,"Reserve policy applies.")));
        var result=capped.evaluate(request(1,Necessity.NEED),healthy());
        assertEquals(Recommendation.WAIT,result.recommendation());assertTrue(result.explanationLines().stream().anyMatch(line->line.contains("Score suggested BUY, limited to WAIT because")));
    }
    @Test void explanationOrderingAndFallbackWarningAreConsistent(){
        var fallback=snapshot(Money.ofRupees(65000),Money.ofRupees(200000),Money.ofRupees(75000),Money.ofRupees(5000),Money.ofRupees(100000),List.of(),BehaviourType.SMART_SHOPPER,true);
        var result=engine.evaluate(request(5000,Necessity.NEED),fallback);
        assertTrue(result.explanationLines().get(0).startsWith("FinSense suggests"));
        assertTrue(result.explanationLines().get(result.explanationLines().size()-1).contains("not financial advice"));
        assertTrue(result.warnings().stream().anyMatch(w->w.contains("declared monthly expenses")));
        assertTrue(result.warnings().stream().anyMatch(w->w.contains("No active savings goals")));
    }
}
