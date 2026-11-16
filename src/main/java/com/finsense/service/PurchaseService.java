package com.finsense.service;

import com.finsense.dao.CategoryDAO;
import com.finsense.dao.FeedbackDAO;
import com.finsense.dao.PurchaseDAO;
import com.finsense.decision.DecisionEvaluator;
import com.finsense.decision.DecisionResult;
import com.finsense.decision.PurchaseRequest;
import com.finsense.exception.ValidationException;
import com.finsense.model.Category;
import com.finsense.model.Money;
import com.finsense.model.Purchase;
import com.finsense.model.PurchaseEvaluation;
import com.finsense.model.PurchaseHistory;
import com.finsense.model.PurchaseSummary;
import com.finsense.model.enums.Necessity;
import com.finsense.model.enums.PurchaseFeedback;
import com.finsense.model.enums.PurchaseStatus;
import com.finsense.model.enums.TransactionType;
import com.finsense.util.SessionManager;
import com.finsense.util.ValidationUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The purchase-decision use case: validate the input, ask the decision engine,
 * and save the verdict together with a frozen snapshot of the finances used.
 * Also handles the purchase life-cycle (bought / dropped) and the user's feedback.
 */
public class PurchaseService {

    private final PurchaseDAO purchaseDAO;
    private final CategoryDAO categoryDAO;
    private final DecisionEvaluator evaluator;
    private final BehaviourService behaviourService;
    private final FeedbackDAO feedbackDAO = new FeedbackDAO();

    public PurchaseService() {
        this(new PurchaseDAO(), new CategoryDAO(), new DecisionEvaluator(), new BehaviourService());
    }

    public PurchaseService(PurchaseDAO purchaseDAO, CategoryDAO categoryDAO, DecisionEvaluator evaluator) {
        this(purchaseDAO, categoryDAO, evaluator, new BehaviourService());
    }

    public PurchaseService(PurchaseDAO purchaseDAO, CategoryDAO categoryDAO, DecisionEvaluator evaluator,
                           BehaviourService behaviourService) {
        this.purchaseDAO = purchaseDAO;
        this.categoryDAO = categoryDAO;
        this.evaluator = evaluator;
        this.behaviourService = behaviourService;
    }

    private long userId() {
        return SessionManager.getInstance().requireUserId();
    }

    public List<Category> categories() {
        return categoryDAO.findByType(TransactionType.EXPENSE, userId());
    }

    public PurchaseEvaluation evaluate(String name, Long categoryId, String price, Necessity necessity,
                                       String description) {
        long uid = userId();
        String cleanName = ValidationUtil.requireText(name, "Product name", 100);
        Category category = expenseCategory(categoryId, uid);
        Money amount = ValidationUtil.parsePositiveAmount(price, "Price");
        Necessity need = ValidationUtil.requireSelection(necessity, "Need or want");
        String note = ValidationUtil.optionalText(description, "Description", 500);

        DecisionResult result = evaluator.evaluate(new PurchaseRequest(cleanName, category, amount, need, note));
        Purchase draft = new Purchase(null, uid, cleanName, category, amount, note, need,
                PurchaseStatus.EVALUATED, LocalDateTime.now());
        long id = purchaseDAO.saveEvaluation(uid, draft, result);
        return new PurchaseEvaluation(id, result);
    }

    private Category expenseCategory(Long categoryId, long uid) {
        if (categoryId == null) {
            throw new ValidationException("Please select a category.");
        }
        Category category = categoryDAO.findVisibleById(categoryId, uid)
                .orElseThrow(() -> new ValidationException("Category not found."));
        if (category.type() != TransactionType.EXPENSE) {
            throw new ValidationException("Choose an expense category.");
        }
        return category;
    }

    public List<Purchase> purchases() {
        return purchaseDAO.findByUserId(userId());
    }

    public Purchase find(long id) {
        return purchaseDAO.findById(id, userId())
                .orElseThrow(() -> new ValidationException("Purchase not found."));
    }

    public long historyCount(long purchaseId) {
        return purchaseDAO.countHistory(purchaseId, userId());
    }

    public List<PurchaseSummary> summaries() {
        return purchaseDAO.summaries(userId());
    }

    public List<PurchaseHistory> history(long purchaseId) {
        find(purchaseId);                                  // ownership check
        return purchaseDAO.history(purchaseId, userId());
    }

    /** Evaluates the same purchase again against today's finances and appends it to the history. */
    public DecisionResult reevaluate(long purchaseId) {
        long uid = userId();
        Purchase purchase = find(purchaseId);
        DecisionResult result = evaluator.evaluate(new PurchaseRequest(purchase.productName(), purchase.category(),
                purchase.price(), purchase.necessity(), purchase.description()));
        purchaseDAO.insertHistory(purchaseId, uid, result);
        return result;
    }

    public void markAsPurchased(long purchaseId, boolean deductFromSavings) {
        purchaseDAO.markAsPurchased(purchaseId, userId(), deductFromSavings, LocalDate.now());
        behaviourService.refreshSpendingClassification();
    }

    public void markAsDropped(long purchaseId) {
        Purchase purchase = find(purchaseId);
        if (purchase.status() == PurchaseStatus.PURCHASED) {
            throw new ValidationException("A purchased item cannot be marked as dropped.");
        }
        if (!purchaseDAO.updateStatus(purchaseId, userId(), PurchaseStatus.DROPPED)) {
            throw new ValidationException("Purchase not found.");
        }
    }

    /** Records how the user felt about something they bought - future advice learns from it. */
    public void rate(long purchaseId, PurchaseFeedback feedback) {
        ValidationUtil.requireSelection(feedback, "Feedback");
        if (find(purchaseId).status() != PurchaseStatus.PURCHASED) {
            throw new ValidationException("Only purchased items can be rated.");
        }
        feedbackDAO.save(purchaseId, userId(), feedback);
        behaviourService.refreshSpendingClassification();
    }
}
