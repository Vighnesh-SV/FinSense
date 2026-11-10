package com.finsense.service;

import com.finsense.dao.FinancialProfileDAO;
import com.finsense.dao.TransactionDAO;
import com.finsense.dao.UserDAO;
import com.finsense.database.DatabaseConnection;
import com.finsense.database.DatabaseInitializer;
import com.finsense.database.TestDatabase;
import com.finsense.model.FinancialProfile;
import com.finsense.model.Money;
import com.finsense.model.User;
import com.finsense.model.enums.Necessity;
import com.finsense.model.enums.PurchaseStatus;
import com.finsense.model.enums.TransactionType;
import com.finsense.util.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.sql.Statement;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class PurchaseHistoryServiceTest {
    private DatabaseConnection db;private PurchaseService service;private FinancialProfileDAO profiles;private long userId,purchaseId,categoryId;
    @BeforeEach void setUp(){
        db=TestDatabase.freshEmptyDatabase();new DatabaseInitializer(db).initialize();
        userId=new UserDAO().insert("history_user","hash","salt","History User");
        SessionManager.getInstance().login(new User(userId,"history_user","History User",LocalDateTime.now()));
        profiles=new FinancialProfileDAO();saveProfile(100000,30000,150000,10000);service=new PurchaseService();
        categoryId=service.categories().stream().filter(c->c.name().equals("Electronics")).findFirst().orElseThrow().id();
        purchaseId=service.evaluate("Phone",categoryId,"25000",Necessity.WANT,"phone test").purchaseId();
    }
    @AfterEach void tearDown(){SessionManager.getInstance().logout();db.close();}
    @Test void reevaluationAddsHistoryAndOldSnapshotSurvivesProfileChange(){
        assertEquals(1,service.summaries().size());
        Money oldDisposable=service.history(purchaseId).get(0).snapshotDisposable();
        saveProfile(200000,30000,150000,10000);service.reevaluate(purchaseId);
        var rows=service.history(purchaseId);assertEquals(2,rows.size());
        assertTrue(rows.stream().anyMatch(h->h.snapshotDisposable().equals(oldDisposable)));
        assertTrue(rows.stream().anyMatch(h->h.snapshotDisposable().equals(Money.ofRupees(160000))));
    }
    @Test void markPurchasedWritesExpenseAndDeductsOnlyFreeSavings(){
        service.markAsPurchased(purchaseId,true);
        assertEquals(PurchaseStatus.PURCHASED,service.find(purchaseId).status());
        assertEquals(Money.ofRupees(125000),profiles.findByUserId(userId).orElseThrow().getCurrentSavings());
        assertEquals(1,new TransactionDAO().find(userId,null,null,TransactionType.EXPENSE,null,"Phone").size());
    }
    @Test void failureAfterExpenseInsertRollsBackEveryChange() throws Exception {
        try(Statement statement=db.getConnection().createStatement()){
            statement.execute("CREATE TRIGGER fail_purchase_status BEFORE UPDATE ON purchases FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced failure'");
        }
        assertThrows(RuntimeException.class,()->service.markAsPurchased(purchaseId,false));
        assertEquals(PurchaseStatus.EVALUATED,service.find(purchaseId).status());
        assertTrue(new TransactionDAO().find(userId,null,null,TransactionType.EXPENSE,null,"Phone").isEmpty());
    }
    private void saveProfile(long income,long expenses,long savings,long target){
        profiles.upsert(new FinancialProfile(userId,Money.ofRupees(income),Money.ofRupees(expenses),Money.ZERO,
                Money.ofRupees(savings),Money.ofRupees(target),null));
    }
}
