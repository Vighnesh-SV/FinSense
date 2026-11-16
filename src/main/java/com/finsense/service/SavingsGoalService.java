package com.finsense.service;

import com.finsense.dao.FinancialProfileDAO;
import com.finsense.dao.SavingsGoalDAO;
import com.finsense.exception.ValidationException;
import com.finsense.model.FinancialProfile;
import com.finsense.model.GoalContribution;
import com.finsense.model.Money;
import com.finsense.model.SavingsGoal;
import com.finsense.model.enums.GoalPriority;
import com.finsense.model.enums.GoalStatus;
import com.finsense.util.SessionManager;
import com.finsense.util.ValidationUtil;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Savings-goal use cases. A contribution does not spend money - it reserves part of
 * the user's existing savings for a goal, which reduces the "free" savings.
 */
public class SavingsGoalService {

    private final SavingsGoalDAO goalDAO;
    private final FinancialProfileDAO profileDAO;

    public SavingsGoalService() {
        this(new SavingsGoalDAO(), new FinancialProfileDAO());
    }

    public SavingsGoalService(SavingsGoalDAO goalDAO, FinancialProfileDAO profileDAO) {
        this.goalDAO = goalDAO;
        this.profileDAO = profileDAO;
    }

    private long userId() {
        return SessionManager.getInstance().requireUserId();
    }

    public List<SavingsGoal> goals() {
        return goalDAO.findByUserId(userId());
    }

    public List<GoalContribution> contributions(long goalId) {
        long uid = userId();
        requireGoal(goalId, uid);
        return goalDAO.contributions(goalId, uid);
    }

    /** Total savings minus everything already reserved for active goals (never negative). */
    public Money freeSavings() {
        long uid = userId();
        Money total = profileDAO.findByUserId(uid).map(FinancialProfile::getCurrentSavings).orElse(Money.ZERO);
        Money reserved = Money.ofPaise(goalDAO.activeContributionsTotal(uid));
        return Money.max(Money.ZERO, total.minus(reserved));
    }

    public SavingsGoal create(String name, String target, LocalDate deadline, GoalPriority priority) {
        long uid = userId();
        String cleanName = ValidationUtil.requireText(name, "Goal name", 100);
        Money amount = ValidationUtil.parsePositiveAmount(target, "Target amount");
        LocalDate due = ValidationUtil.requireDate(deadline, "Deadline");
        GoalPriority selected = ValidationUtil.requireSelection(priority, "Priority");
        long id = goalDAO.insert(uid, cleanName, amount, due, selected);
        return goalDAO.findById(id, uid).orElseThrow();
    }

    public SavingsGoal update(long id, String name, String target, LocalDate deadline, GoalPriority priority) {
        long uid = userId();
        requireGoal(id, uid);
        String cleanName = ValidationUtil.requireText(name, "Goal name", 100);
        Money amount = ValidationUtil.parsePositiveAmount(target, "Target amount");
        LocalDate due = ValidationUtil.requireDate(deadline, "Deadline");
        GoalPriority selected = ValidationUtil.requireSelection(priority, "Priority");
        if (!goalDAO.update(id, uid, cleanName, amount, due, selected)) {
            throw new ValidationException("Only active goals can be edited.");
        }
        return goalDAO.findById(id, uid).orElseThrow();
    }

    public void cancel(long id) {
        if (!goalDAO.cancel(id, userId())) {
            throw new ValidationException("Active goal not found.");
        }
    }

    public SavingsGoal contribute(long id, String amount, LocalDate date, String note) {
        long uid = userId();
        SavingsGoal goal = requireGoal(id, uid);
        if (goal.status() != GoalStatus.ACTIVE) {
            throw new ValidationException("Contributions can only be added to active goals.");
        }
        Money contribution = ValidationUtil.parsePositiveAmount(amount, "Contribution");
        LocalDate on = ValidationUtil.requireDate(date, "Contribution date");
        String cleanNote = ValidationUtil.optionalText(note, "Note", 255);
        goalDAO.contribute(id, uid, contribution, on, cleanNote);
        return goalDAO.findById(id, uid).orElseThrow();
    }

    /** Amount to set aside each month (including this one) to reach the target by the deadline. */
    public Money requiredMonthlyAmount(SavingsGoal goal, LocalDate from) {
        long months = Math.max(1,
                ChronoUnit.MONTHS.between(from.withDayOfMonth(1), goal.deadline().withDayOfMonth(1)) + 1);
        return goal.remainingAmount().divide(months);
    }

    private SavingsGoal requireGoal(long id, long uid) {
        return goalDAO.findById(id, uid).orElseThrow(() -> new ValidationException("Goal not found."));
    }
}
