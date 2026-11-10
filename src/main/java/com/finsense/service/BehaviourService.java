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

/** Questionnaire and spending-based behaviour classification use cases. */
public class BehaviourService {
    private final BehaviourDAO dao;
    private final QuestionnaireClassifier classifier;
    public BehaviourService() { this(new BehaviourDAO(),new QuestionnaireClassifier()); }
    public BehaviourService(BehaviourDAO dao,QuestionnaireClassifier classifier) { this.dao=dao;this.classifier=classifier; }
    private long userId() { return SessionManager.getInstance().requireUserId(); }
    public boolean hasQuestionnaire() { return dao.hasQuestionnaire(userId()); }
    public QuestionnaireResult submitQuestionnaire(List<Integer> answers) {
        long uid=userId(); QuestionnaireResult result=classifier.score(answers); dao.insertQuestionnaire(uid,result);
        BehaviourType type=classifier.classify(result.impulsivityScore(),result.volumeScore(),result.disciplineScore());
        dao.insertProfile(uid,type,result.impulsivityScore(),result.volumeScore(),result.disciplineScore(),BehaviourSource.QUESTIONNAIRE,
                "Based on your answers to the spending habits questionnaire.");
        return result;
    }
    public Optional<BehaviourProfile> currentProfile() { return dao.latestProfile(userId()); }
    public List<BehaviourProfile> history() { return dao.history(userId()); }
    /**
     * Blends spending observations and questionnaire scores 50/50 after 30 days of history.
     * A new history row is inserted only when the resulting type changes.
     */
    public Optional<BehaviourProfile> refreshSpendingClassification() {
        long uid=userId(); QuestionnaireResult questionnaire=dao.latestQuestionnaire(uid).orElse(null);
        if(questionnaire==null) return dao.latestProfile(uid);
        SpendingSignals signals=dao.spendingSignals(uid,LocalDate.now());
        if(!signals.hasThirtyDays(LocalDate.now())) return dao.latestProfile(uid);
        int regret=clamp((int)Math.round(signals.regretShare()*100.0));
        int spendingVolume=clamp((int)Math.round((signals.discretionarySpendShare()*100.0+
                signals.discretionaryTransactionShare()*100.0+regret)/3.0));
        int spendingDiscipline=100-spendingVolume;
        int impulse=blendScore(questionnaire.impulsivityScore(),regret);
        int volume=blendScore(questionnaire.volumeScore(),spendingVolume);
        int discipline=blendScore(questionnaire.disciplineScore(),spendingDiscipline);
        BehaviourType type=classifier.classify(impulse,volume,discipline);
        Optional<BehaviourProfile> latest=dao.latestProfile(uid);
        if(latest.isEmpty() || latest.get().type()!=type) {
            String reason=String.format("Thirty-day spending signals changed the classification (discretionary spending %.0f%%; regretted purchases %d of %d).",
                    signals.discretionarySpendShare()*100.0,signals.regrettedPurchases(),signals.purchasedCount());
            dao.insertProfile(uid,type,impulse,volume,discipline,BehaviourSource.SPENDING,reason);
            return dao.latestProfile(uid);
        }
        return latest;
    }
    public static int blendScore(int questionnaire,int spending) { return (questionnaire+spending+1)/2; }
    private static int clamp(int value) { return Math.max(0,Math.min(100,value)); }
}
