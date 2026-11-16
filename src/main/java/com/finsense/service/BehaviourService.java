package com.finsense.service;

import com.finsense.dao.BehaviourDAO;
import com.finsense.decision.QuestionnaireClassifier;
import com.finsense.model.BehaviourProfile;
import com.finsense.model.QuestionnaireResult;
import com.finsense.model.SpendingSignals;
import com.finsense.model.enums.BehaviourSource;
import com.finsense.model.enums.BehaviourType;
import com.finsense.util.SessionManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spending-personality use cases: score the questionnaire, then keep the classification
 * up to date from real spending once 30 days of history exist.
 */
public class BehaviourService {

    private final BehaviourDAO dao;
    private final QuestionnaireClassifier classifier;

    public BehaviourService() {
        this(new BehaviourDAO(), new QuestionnaireClassifier());
    }

    public BehaviourService(BehaviourDAO dao, QuestionnaireClassifier classifier) {
        this.dao = dao;
        this.classifier = classifier;
    }

    private long userId() {
        return SessionManager.getInstance().requireUserId();
    }

    public boolean hasQuestionnaire() {
        return dao.hasQuestionnaire(userId());
    }

    public QuestionnaireResult submitQuestionnaire(List<Integer> answers) {
        long uid = userId();
        QuestionnaireResult result = classifier.score(answers);
        dao.insertQuestionnaire(uid, result);
        BehaviourType type = classifier.classify(result.impulsivityScore(), result.volumeScore(),
                result.disciplineScore());
        dao.insertProfile(uid, type, result.impulsivityScore(), result.volumeScore(), result.disciplineScore(),
                BehaviourSource.QUESTIONNAIRE, "Based on your answers to the spending habits questionnaire.");
        return result;
    }

    public Optional<BehaviourProfile> currentProfile() {
        return dao.latestProfile(userId());
    }

    public List<BehaviourProfile> history() {
        return dao.history(userId());
    }

    /**
     * Blends 30-day spending signals 50/50 with the questionnaire scores. A new history
     * row is written only when the resulting type changes. Called after login and after
     * purchases/feedback - not on every screen load.
     */
    public Optional<BehaviourProfile> refreshSpendingClassification() {
        long uid = userId();
        Optional<QuestionnaireResult> questionnaire = dao.latestQuestionnaire(uid);
        if (questionnaire.isEmpty()) {
            return dao.latestProfile(uid);
        }
        SpendingSignals signals = dao.spendingSignals(uid, LocalDate.now());
        if (!signals.hasThirtyDays(LocalDate.now())) {
            return dao.latestProfile(uid);
        }

        int regret = clamp((int) Math.round(signals.regretShare() * 100.0));
        int spendingVolume = clamp((int) Math.round((signals.discretionarySpendShare() * 100.0
                + signals.discretionaryTransactionShare() * 100.0 + regret) / 3.0));
        int spendingDiscipline = 100 - spendingVolume;

        QuestionnaireResult answers = questionnaire.get();
        int impulse = blendScore(answers.impulsivityScore(), regret);
        int volume = blendScore(answers.volumeScore(), spendingVolume);
        int discipline = blendScore(answers.disciplineScore(), spendingDiscipline);
        BehaviourType type = classifier.classify(impulse, volume, discipline);

        Optional<BehaviourProfile> latest = dao.latestProfile(uid);
        if (latest.isPresent() && latest.get().type() == type) {
            return latest;
        }
        String reason = String.format("Thirty-day spending changed the classification "
                        + "(discretionary spending %.0f%%; regretted purchases %d of %d).",
                signals.discretionarySpendShare() * 100.0, signals.regrettedPurchases(), signals.purchasedCount());
        dao.insertProfile(uid, type, impulse, volume, discipline, BehaviourSource.SPENDING, reason);
        return dao.latestProfile(uid);
    }

    /** Average of two 0-100 scores, rounded half up. */
    public static int blendScore(int questionnaire, int spending) {
        return (questionnaire + spending + 1) / 2;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
