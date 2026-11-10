package com.finsense.service;

import com.finsense.dao.CommitmentDAO;
import com.finsense.dao.FinancialProfileDAO;
import com.finsense.exception.IncompleteProfileException;
import com.finsense.exception.ValidationException;
import com.finsense.model.Commitment;
import com.finsense.model.FinancialProfile;
import com.finsense.model.FinancialSummary;
import com.finsense.model.Money;
import com.finsense.model.enums.CommitmentType;
import com.finsense.util.SessionManager;
import com.finsense.util.ValidationUtil;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service managing financial profiles, recurring commitments, and financial summary calculations.
 *
 * Architecture & OOP concepts:
 * 1. Constructor Injection / IoC: DAOs are injected via constructor, facilitating unit and integration testing.
 * 2. Separation of Concerns & User Isolation: Current user ID is retrieved from SessionManager.
 * 3. Pure Static Helper: {@link #summarize} performs the core financial arithmetic without database
 *    or session dependencies, allowing fast, deterministic testing without side effects.
 */
public class FinancialService {

    /**
     * Recommended emergency reserve: 3 months of essential expenses (fixed + variable + commitments).
     * Threshold will be moved to DecisionThresholds in Phase 9.
     */
    public static final int EMERGENCY_MONTHS = 3;

    private final FinancialProfileDAO profileDAO;
    private final CommitmentDAO commitmentDAO;

    /** Production constructor using default DAO instances. */
    public FinancialService() {
        this(new FinancialProfileDAO(), new CommitmentDAO());
    }

    /** Constructor injection for testing and flexibility. */
    public FinancialService(FinancialProfileDAO profileDAO, CommitmentDAO commitmentDAO) {
        this.profileDAO = profileDAO;
        this.commitmentDAO = commitmentDAO;
    }

    private long currentUserId() {
        return SessionManager.getInstance().requireUserId();
    }

    // ---------------------------------------------------------------- Profile

    /** Retrieves the current user's financial profile. */
    public Optional<FinancialProfile> getProfile() {
        return profileDAO.findByUserId(currentUserId());
    }

    /** Returns true if a profile exists and has positive monthly income. */
    public boolean hasProfile() {
        return getProfile()
                .map(p -> p.getMonthlyIncome().isPositive())
                .orElse(false);
    }

    /**
     * Validates and saves the financial profile for the logged-in user.
     *
     * Rules:
     * - Monthly income must be > 0.
     * - All other fields must be >= 0.
     * - Monthly savings target cannot exceed monthly income.
     */
    public FinancialProfile saveProfile(String income, String fixed, String variable, String savings, String target) {
        long userId = currentUserId();

        Money monthlyIncome = ValidationUtil.parsePositiveAmount(income, "Monthly income");
        Money fixedExpenses = ValidationUtil.parseNonNegativeAmount(fixed, "Fixed expenses");
        Money variableExpenses = ValidationUtil.parseNonNegativeAmount(variable, "Variable expenses");
        Money currentSavings = ValidationUtil.parseNonNegativeAmount(savings, "Current savings");
        Money monthlySavingsTarget = ValidationUtil.parseNonNegativeAmount(target, "Monthly savings target");

        if (monthlySavingsTarget.isGreaterThan(monthlyIncome)) {
            throw new ValidationException("Monthly savings target", "Savings target cannot exceed monthly income.");
        }

        FinancialProfile profile = new FinancialProfile(
                userId,
                monthlyIncome,
                fixedExpenses,
                variableExpenses,
                currentSavings,
                monthlySavingsTarget,
                null
        );

        profileDAO.upsert(profile);
        return profile;
    }

    // ---------------------------------------------------------------- Commitments

    /** Retrieves all commitments belonging to the current user. */
    public List<Commitment> getCommitments() {
        return commitmentDAO.findByUserId(currentUserId());
    }

    /**
     * Adds a new commitment for the current user.
     */
    public Commitment addCommitment(String name, CommitmentType type, String amount, LocalDate start, LocalDate end) {
        long userId = currentUserId();

        String cleanName = ValidationUtil.requireText(name, "Name", 100);
        CommitmentType cleanType = ValidationUtil.requireSelection(type, "Type");
        Money monthlyAmount = ValidationUtil.parsePositiveAmount(amount, "Monthly amount");
        LocalDate startDate = ValidationUtil.requireDate(start, "Start date");

        if (end != null && end.isBefore(startDate)) {
            throw new ValidationException("End date", "End date cannot be before start date.");
        }

        Commitment commitment = new Commitment(null, userId, cleanName, cleanType, monthlyAmount, startDate, end);
        long id = commitmentDAO.insert(commitment);
        return new Commitment(id, userId, cleanName, cleanType, monthlyAmount, startDate, end);
    }

    /**
     * Updates an existing commitment. Throws ValidationException if not found or not owned by user.
     */
    public Commitment updateCommitment(long id, String name, CommitmentType type, String amount, LocalDate start, LocalDate end) {
        long userId = currentUserId();

        // Check ownership
        commitmentDAO.findById(id, userId)
                .orElseThrow(() -> new ValidationException("Commitment not found."));

        String cleanName = ValidationUtil.requireText(name, "Name", 100);
        CommitmentType cleanType = ValidationUtil.requireSelection(type, "Type");
        Money monthlyAmount = ValidationUtil.parsePositiveAmount(amount, "Monthly amount");
        LocalDate startDate = ValidationUtil.requireDate(start, "Start date");

        if (end != null && end.isBefore(startDate)) {
            throw new ValidationException("End date", "End date cannot be before start date.");
        }

        Commitment commitment = new Commitment(id, userId, cleanName, cleanType, monthlyAmount, startDate, end);
        boolean updated = commitmentDAO.update(commitment);
        if (!updated) {
            throw new ValidationException("Commitment not found.");
        }
        return commitment;
    }

    /**
     * Deletes a commitment. Throws ValidationException if not found or not owned by user.
     */
    public void deleteCommitment(long id) {
        long userId = currentUserId();
        boolean deleted = commitmentDAO.delete(id, userId);
        if (!deleted) {
            throw new ValidationException("Commitment not found.");
        }
    }

    /**
     * Calculates the sum of monthly commitments active on the given date using Java Streams.
     */
    public Money activeCommitmentsTotal(LocalDate onDate) {
        return getCommitments().stream()
                .filter(c -> c.isActiveOn(onDate))
                .map(Commitment::getMonthlyAmount)
                .reduce(Money.ZERO, Money::plus);
    }

    // ---------------------------------------------------------------- Summary Calculation

    /** Calculates the financial summary for today. */
    public FinancialSummary calculateSummary() {
        return calculateSummary(LocalDate.now());
    }

    /**
     * Calculates the financial summary as of the specified date.
     * Throws IncompleteProfileException if the user has not completed their profile.
     */
    public FinancialSummary calculateSummary(LocalDate onDate) {
        FinancialProfile profile = getProfile()
                .filter(p -> p.getMonthlyIncome().isPositive())
                .orElseThrow(() -> new IncompleteProfileException("Please complete your financial profile first."));

        List<Commitment> commitments = getCommitments();
        return summarize(profile, commitments, onDate);
    }

    /**
     * Pure static calculation helper with zero database / session side-effects.
     *
     * Formulas:
     * - disposable = income - totalExpenses - activeCommitments - savingsTarget (can be negative; never clamped)
     * - emergencyReserve = 3 * (fixed + variable + activeCommitments)
     * - commitmentRatio = activeCommitments.ratioTo(income)
     */
    public static FinancialSummary summarize(FinancialProfile p, List<Commitment> commitments, LocalDate onDate) {
        Money income = p.getMonthlyIncome();
        Money totalExpenses = p.totalExpenses();

        Money activeCommitments = commitments.stream()
                .filter(c -> c.isActiveOn(onDate))
                .map(Commitment::getMonthlyAmount)
                .reduce(Money.ZERO, Money::plus);

        Money savingsTarget = p.getMonthlySavingsTarget();

        // disposable = income - totalExpenses - activeCommitments - savingsTarget
        Money disposable = income
                .minus(totalExpenses)
                .minus(activeCommitments)
                .minus(savingsTarget);

        // emergencyReserve = 3 * (fixed + variable + activeCommitments)
        Money monthlyBaseline = totalExpenses.plus(activeCommitments);
        Money emergencyReserve = monthlyBaseline.times(EMERGENCY_MONTHS);

        double commitmentRatio = activeCommitments.ratioTo(income);

        return new FinancialSummary(
                income,
                totalExpenses,
                activeCommitments,
                savingsTarget,
                disposable,
                commitmentRatio,
                emergencyReserve
        );
    }
}

