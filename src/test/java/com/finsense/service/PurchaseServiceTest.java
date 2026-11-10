package com.finsense.service;

import com.finsense.dao.FinancialProfileDAO;
import com.finsense.dao.UserDAO;
import com.finsense.database.DatabaseConnection;
import com.finsense.database.DatabaseInitializer;
import com.finsense.database.TestDatabase;
import com.finsense.model.FinancialProfile;
import com.finsense.model.Money;
import com.finsense.model.User;
import com.finsense.model.enums.Necessity;
import com.finsense.util.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class PurchaseServiceTest {
    private DatabaseConnection db;private PurchaseService service;
    @BeforeEach void setUp(){
        db=TestDatabase.freshEmptyDatabase();new DatabaseInitializer(db).initialize();
        long userId=new UserDAO().insert("purchase_user","hash","salt","Purchase User");
        SessionManager.getInstance().login(new User(userId,"purchase_user","Purchase User",LocalDateTime.now()));
        new FinancialProfileDAO().upsert(new FinancialProfile(userId,Money.ofRupees(100000),Money.ofRupees(20000),Money.ofRupees(10000),Money.ofRupees(150000),Money.ofRupees(10000),null));
        service=new PurchaseService();
    }
    @AfterEach void tearDown(){SessionManager.getInstance().logout();db.close();}
    @Test void evaluationPersistsPurchaseAndFrozenHistory(){
        long category=service.categories().stream().filter(c->c.name().equals("Electronics")).findFirst().orElseThrow().id();
        var evaluation=service.evaluate("Phone",category,"25000",Necessity.WANT,"replace old phone");
        assertTrue(evaluation.purchaseId()>0);assertEquals(1,service.historyCount(evaluation.purchaseId()));
        var purchase=service.find(evaluation.purchaseId());assertEquals("Phone",purchase.productName());
        assertEquals(Money.ofRupees(25000),purchase.price());assertEquals(Necessity.WANT,purchase.necessity());
        assertTrue(evaluation.decision().snapshot().fallbackDataUsed());
        assertEquals(Money.ofRupees(60000),evaluation.decision().snapshot().disposableIncome());
    }
}
