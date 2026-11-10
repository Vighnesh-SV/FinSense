package com.finsense.service;

import com.finsense.dao.BehaviourDAO;
import com.finsense.dao.CategoryDAO;
import com.finsense.dao.TransactionDAO;
import com.finsense.dao.UserDAO;
import com.finsense.database.DatabaseConnection;
import com.finsense.database.DatabaseInitializer;
import com.finsense.database.TestDatabase;
import com.finsense.model.User;
import com.finsense.model.enums.BehaviourSource;
import com.finsense.model.enums.TransactionType;
import com.finsense.util.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BehaviourServiceTest {
    private DatabaseConnection db;private BehaviourService service;private BehaviourDAO dao;private TransactionDAO txns;private long userId;
    @BeforeEach void setUp(){
        db=TestDatabase.freshEmptyDatabase();new DatabaseInitializer(db).initialize();
        userId=new UserDAO().insert("behaviour_user","hash","salt","Behaviour User");
        SessionManager.getInstance().login(new User(userId,"behaviour_user","Behaviour User",LocalDateTime.now()));
        dao=new BehaviourDAO();txns=new TransactionDAO();service=new BehaviourService(dao,new com.finsense.decision.QuestionnaireClassifier());
    }
    @AfterEach void tearDown(){SessionManager.getInstance().logout();db.close();}
    @Test void blendsQuestionnaireAndSpendingScoresEvenly(){assertEquals(70,BehaviourService.blendScore(80,60));}
    @Test void spendingTypeChangeAddsHistoryAndSameTypeAddsNothing(){
        List<Integer> smart=List.of(1,1,5,5,1,1,1,1,5,5,5,5);
        service.submitQuestionnaire(smart);
        long category=new CategoryDAO().findByType(TransactionType.EXPENSE,userId).stream().filter(c->c.name().equals("Groceries")).findFirst().orElseThrow().id();
        txns.insert(userId,category,TransactionType.EXPENSE,com.finsense.model.Money.ofRupees(100),LocalDate.now().minusDays(40),"old expense");
        service.refreshSpendingClassification(); assertEquals(1,service.history().size());
        List<Integer> heavy=List.of(1,1,5,5,5,5,5,5,5,5,5,5);
        service.submitQuestionnaire(heavy); assertEquals(com.finsense.model.enums.BehaviourType.HEAVY_BUYER,service.currentProfile().orElseThrow().type());
        txns.insert(userId,category,TransactionType.EXPENSE,com.finsense.model.Money.ofRupees(100),LocalDate.now(),"recent essential");
        var changed=service.refreshSpendingClassification().orElseThrow();
        assertEquals(com.finsense.model.enums.BehaviourType.SMART_SHOPPER,changed.type()); assertEquals(BehaviourSource.SPENDING,changed.source());
        assertEquals(3,service.history().size());
        service.refreshSpendingClassification(); assertEquals(3,service.history().size());
    }
}
