package com.finsense.service;

import com.finsense.dao.CategoryDAO;
import com.finsense.dao.PurchaseDAO;
import com.finsense.decision.DecisionEvaluator;
import com.finsense.decision.DecisionResult;
import com.finsense.decision.PurchaseRequest;
import com.finsense.exception.ValidationException;
import com.finsense.model.Category;
import com.finsense.model.Purchase;
import com.finsense.model.PurchaseEvaluation;
import com.finsense.model.PurchaseHistory;
import com.finsense.model.PurchaseSummary;
import com.finsense.model.enums.Necessity;
import com.finsense.model.enums.PurchaseStatus;
import com.finsense.model.enums.TransactionType;
import com.finsense.util.SessionManager;
import com.finsense.util.ValidationUtil;
import java.time.LocalDateTime;
import java.util.List;

/** Purchase evaluation use case: validate, evaluate, then persist a frozen result snapshot. */
public class PurchaseService {
    private final PurchaseDAO purchaseDAO;private final CategoryDAO categoryDAO;private final DecisionEvaluator evaluator;private final BehaviourService behaviourService;
    public PurchaseService(){this(new PurchaseDAO(),new CategoryDAO(),new DecisionEvaluator(),new BehaviourService());}
    public PurchaseService(PurchaseDAO purchaseDAO,CategoryDAO categoryDAO,DecisionEvaluator evaluator){this(purchaseDAO,categoryDAO,evaluator,new BehaviourService());}
    public PurchaseService(PurchaseDAO purchaseDAO,CategoryDAO categoryDAO,DecisionEvaluator evaluator,BehaviourService behaviourService){this.purchaseDAO=purchaseDAO;this.categoryDAO=categoryDAO;this.evaluator=evaluator;this.behaviourService=behaviourService;}
    private long userId(){return SessionManager.getInstance().requireUserId();}
    public List<Category> categories(){return categoryDAO.findByType(TransactionType.EXPENSE,userId());}
    public PurchaseEvaluation evaluate(String name,Long categoryId,String price,Necessity necessity,String description){
        long uid=userId();String cleanName=ValidationUtil.requireText(name,"Product name",100);
        if(categoryId==null)throw new ValidationException("Please select a category.");
        Category category=categoryDAO.findVisibleById(categoryId,uid).orElseThrow(()->new ValidationException("Category not found."));
        if(category.type()!=TransactionType.EXPENSE)throw new ValidationException("Choose an expense category.");
        var amount=ValidationUtil.parsePositiveAmount(price,"Price");
        var need=ValidationUtil.requireSelection(necessity,"Necessity");
        String note=ValidationUtil.optionalText(description,"Description",500);
        PurchaseRequest request=new PurchaseRequest(cleanName,category,amount,need,note);
        DecisionResult result=evaluator.evaluate(request);
        Purchase draft=new Purchase(null,uid,cleanName,category,amount,note,need,PurchaseStatus.EVALUATED,LocalDateTime.now());
        long id=purchaseDAO.saveEvaluation(uid,draft,result);
        return new PurchaseEvaluation(id,result);
    }
    public List<Purchase> purchases(){return purchaseDAO.findByUserId(userId());}
    public Purchase find(long id){return purchaseDAO.findById(id,userId()).orElseThrow(()->new ValidationException("Purchase not found."));}
    public long historyCount(long purchaseId){return purchaseDAO.countHistory(purchaseId,userId());}
    public List<PurchaseSummary> summaries(){return purchaseDAO.summaries(userId());}
    public List<PurchaseHistory> history(long purchaseId){find(purchaseId);return purchaseDAO.history(purchaseId,userId());}
    public DecisionResult reevaluate(long purchaseId){
        long uid=userId();Purchase purchase=purchaseDAO.findById(purchaseId,uid).orElseThrow(()->new ValidationException("Purchase not found."));
        DecisionResult result=evaluator.evaluate(new PurchaseRequest(purchase.productName(),purchase.category(),purchase.price(),purchase.necessity(),purchase.description()));
        purchaseDAO.insertHistory(purchaseId,uid,result);return result;
    }
    public void markAsPurchased(long purchaseId,boolean deductFromSavings){
        long uid=userId();purchaseDAO.markAsPurchased(purchaseId,uid,deductFromSavings,java.time.LocalDate.now());
        behaviourService.refreshSpendingClassification();
    }
    public void markAsDropped(long purchaseId){
        Purchase purchase=find(purchaseId);
        if(purchase.status()==PurchaseStatus.PURCHASED)throw new ValidationException("A purchased item cannot be marked as dropped.");
        if(!purchaseDAO.updateStatus(purchaseId,userId(),PurchaseStatus.DROPPED))throw new ValidationException("Purchase not found.");
    }
}
