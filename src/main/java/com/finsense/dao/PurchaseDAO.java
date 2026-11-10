package com.finsense.dao;

import com.finsense.decision.DecisionResult;
import com.finsense.model.Category;
import com.finsense.model.Purchase;
import com.finsense.model.PurchaseHistory;
import com.finsense.model.PurchaseSummary;
import com.finsense.model.Money;
import com.finsense.model.enums.Necessity;
import com.finsense.model.enums.PurchaseStatus;
import com.finsense.model.enums.TransactionType;
import com.finsense.exception.ValidationException;
import java.util.List;
import java.util.Optional;

/** Purchase and evaluation persistence; all purchase reads are user-scoped. */
public class PurchaseDAO extends BaseDAO {
    private static final String SELECT="SELECT p.id,p.user_id,p.product_name,p.category_id,c.user_id category_user_id,c.name category_name,"+
            "c.type category_type,c.nature,c.is_commitment,p.price,p.description,p.necessity,p.status,p.created_at FROM purchases p JOIN categories c ON c.id=p.category_id ";
    private static final RowMapper<Purchase> MAPPER=rs->new Purchase(rs.getLong("id"),rs.getLong("user_id"),rs.getString("product_name"),
            new Category(rs.getLong("category_id"),getNullableLong(rs,"category_user_id"),rs.getString("category_name"),
                    getEnum(rs,"category_type",com.finsense.model.enums.TransactionType.class),rs.getString("nature"),rs.getBoolean("is_commitment")),
            getMoney(rs,"price"),rs.getString("description"),getEnum(rs,"necessity",Necessity.class),getEnum(rs,"status",PurchaseStatus.class),getDateTime(rs,"created_at"));
    private static final RowMapper<PurchaseHistory> HISTORY_MAPPER=rs->new PurchaseHistory(rs.getLong("id"),rs.getLong("purchase_id"),
            getEnum(rs,"recommendation",com.finsense.decision.Recommendation.class),rs.getInt("score"),rs.getString("explanation"),rs.getString("warnings"),
            getMoney(rs,"snapshot_income"),getMoney(rs,"snapshot_expenses"),getMoney(rs,"snapshot_disposable"),getMoney(rs,"snapshot_free_savings"),
            rs.getString("snapshot_behaviour"),getDateTime(rs,"evaluated_at"));

    public long saveEvaluation(long userId,Purchase purchase,DecisionResult result) {
        return inTransaction(()->{
            long id=insert("INSERT INTO purchases(user_id,product_name,category_id,price,description,necessity) VALUES(?,?,?,?,?,?)",
                    userId,purchase.productName(),purchase.category().id(),purchase.price(),purchase.description(),purchase.necessity());
            insertHistory(id,userId,result); return id;
        });
    }
    public void insertHistory(long purchaseId,long userId,DecisionResult result) {
        if(queryOne("SELECT id FROM purchases WHERE id=? AND user_id=?",rs->rs.getLong(1),purchaseId,userId).isEmpty())
            throw new ValidationException("Purchase not found.");
        var snapshot=result.snapshot();
        String explanation=String.join("\n",result.explanationLines()),warnings=result.warnings().isEmpty()?null:String.join("\n",result.warnings());
        String behaviour=snapshot.behaviour()==null?"UNCLASSIFIED":snapshot.behaviour().name();
        insert("INSERT INTO purchase_history(purchase_id,recommendation,score,explanation,warnings,snapshot_income,snapshot_expenses,"+
                "snapshot_disposable,snapshot_free_savings,snapshot_behaviour,evaluated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                purchaseId,result.recommendation(),result.score(),explanation,warnings,snapshot.income(),snapshot.monthlyExpenses(),
                snapshot.disposableIncome(),snapshot.freeSavings(),behaviour,result.evaluatedAt());
    }
    public List<Purchase> findByUserId(long userId) { return queryList(SELECT+"WHERE p.user_id=? ORDER BY p.created_at DESC,p.id DESC",MAPPER,userId); }
    public Optional<Purchase> findById(long id,long userId) { return queryOne(SELECT+"WHERE p.id=? AND p.user_id=?",MAPPER,id,userId); }
    public boolean updateStatus(long id,long userId,PurchaseStatus status) {
        return update("UPDATE purchases SET status=? WHERE id=? AND user_id=?",status,id,userId)>0;
    }
    public long countHistory(long purchaseId,long userId) {
        return queryLong("SELECT COUNT(*) FROM purchase_history h JOIN purchases p ON p.id=h.purchase_id WHERE h.purchase_id=? AND p.user_id=?",purchaseId,userId);
    }
    public List<PurchaseHistory> history(long purchaseId,long userId) {
        return queryList("SELECT h.id,h.purchase_id,h.recommendation,h.score,h.explanation,h.warnings,h.snapshot_income,h.snapshot_expenses,"+
                "h.snapshot_disposable,h.snapshot_free_savings,h.snapshot_behaviour,h.evaluated_at FROM purchase_history h "+
                "JOIN purchases p ON p.id=h.purchase_id WHERE h.purchase_id=? AND p.user_id=? ORDER BY h.evaluated_at DESC,h.id DESC",
                HISTORY_MAPPER,purchaseId,userId);
    }
    public List<PurchaseSummary> summaries(long userId) {
        String sql=SELECT+"WHERE p.user_id=? ORDER BY p.created_at DESC,p.id DESC";
        return queryList(sql,(rs)->{
            Purchase purchase=MAPPER.map(rs);
            var latest=queryOne("SELECT h.recommendation,h.score FROM purchase_history h JOIN purchases owned ON owned.id=h.purchase_id "+
                            "WHERE owned.user_id=? AND h.purchase_id=? ORDER BY h.evaluated_at DESC,h.id DESC LIMIT 1",
                    r->new Object[]{getEnum(r,"recommendation",com.finsense.decision.Recommendation.class),r.getInt("score")},userId,purchase.id()).orElse(null);
            return new PurchaseSummary(purchase,latest==null?null:(com.finsense.decision.Recommendation)latest[0],latest==null?null:(Integer)latest[1]);
        },userId);
    }
    /** Marking purchased atomically records the expense, optionally deducts free savings, and updates status. */
    public void markAsPurchased(long purchaseId,long userId,boolean deductFromSavings,java.time.LocalDate date) {
        inTransaction(()->{
            Purchase purchase=findById(purchaseId,userId).orElseThrow(()->new ValidationException("Purchase not found."));
            if(purchase.status()==PurchaseStatus.PURCHASED) throw new ValidationException("Purchase is already marked as purchased.");
            if(purchase.status()==PurchaseStatus.DROPPED) throw new ValidationException("A dropped purchase cannot be marked as purchased.");
            if(deductFromSavings) {
                List<Money> savingsRows=queryList("SELECT current_savings FROM financial_profiles WHERE user_id=? FOR UPDATE",rs->getMoney(rs,"current_savings"),userId);
                if(savingsRows.isEmpty()) throw new ValidationException("Complete your financial profile before deducting savings.");
                Money current=savingsRows.get(0);
                Money reserved=Money.ofPaise(queryLong("SELECT COALESCE(SUM(c.amount),0) FROM goal_contributions c JOIN savings_goals g ON g.id=c.goal_id WHERE g.user_id=? AND g.status='ACTIVE'",userId));
                Money free=Money.max(Money.ZERO,current.minus(reserved));
                if(purchase.price().isGreaterThan(free)) throw new ValidationException("Purchase exceeds free savings of "+free.format()+".");
                update("UPDATE financial_profiles SET current_savings=? WHERE user_id=?",current.minus(purchase.price()),userId);
            }
            insert("INSERT INTO transactions(user_id,category_id,type,amount,txn_date,description,purchase_id) VALUES(?,?,?,?,?,?,?)",
                    userId,purchase.category().id(),TransactionType.EXPENSE,purchase.price(),date,"Purchase: "+purchase.productName(),purchaseId);
            if(update("UPDATE purchases SET status='PURCHASED' WHERE id=? AND user_id=? AND status='EVALUATED'",purchaseId,userId)==0)
                throw new ValidationException("Purchase could not be marked as purchased.");
            return null;
        });
    }
}
