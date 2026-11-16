package com.finsense.dao;

import com.finsense.model.BehaviourProfile;
import com.finsense.model.Money;
import com.finsense.model.QuestionnaireResult;
import com.finsense.model.SpendingSignals;
import com.finsense.model.enums.BehaviourSource;
import com.finsense.model.enums.BehaviourType;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Persists questionnaire attempts and behaviour profile history, scoped to user_id. */
public class BehaviourDAO extends BaseDAO {
    private static final RowMapper<BehaviourProfile> PROFILE_MAPPER=rs->new BehaviourProfile(rs.getLong("id"),rs.getLong("user_id"),
            getEnum(rs,"type",BehaviourType.class),rs.getInt("impulse_score"),rs.getInt("volume_score"),rs.getInt("discipline_score"),
            getEnum(rs,"source",BehaviourSource.class),rs.getString("reason"),getDateTime(rs,"created_at"));
    private static final RowMapper<QuestionnaireResult> QUESTIONNAIRE_MAPPER=rs->{
        List<Integer> answers=Arrays.stream(rs.getString("answers").split(",")).map(Integer::parseInt).toList();
        return new QuestionnaireResult(answers,rs.getInt("impulse_score"),rs.getInt("volume_score"),rs.getInt("discipline_score"));
    };
    public boolean hasQuestionnaire(long userId) { return queryLong("SELECT COUNT(*) FROM questionnaires WHERE user_id=?",userId)>0; }
    public Optional<QuestionnaireResult> latestQuestionnaire(long userId) {
        return queryOne("SELECT answers,impulse_score,volume_score,discipline_score FROM questionnaires WHERE user_id=? ORDER BY taken_at DESC,id DESC LIMIT 1",QUESTIONNAIRE_MAPPER,userId);
    }
    public void insertQuestionnaire(long userId,QuestionnaireResult result) {
        String answers=String.join(",",result.answers().stream().map(String::valueOf).toList());
        insert("INSERT INTO questionnaires(user_id,answers,impulse_score,volume_score,discipline_score) VALUES(?,?,?,?,?)",
                userId,answers,result.impulsivityScore(),result.volumeScore(),result.disciplineScore());
    }
    public long insertProfile(long userId,BehaviourType type,int impulse,int volume,int discipline,BehaviourSource source,String reason) {
        return insert("INSERT INTO behaviour_profiles(user_id,type,impulse_score,volume_score,discipline_score,source,reason) VALUES(?,?,?,?,?,?,?)",
                userId,type,impulse,volume,discipline,source,reason);
    }
    public Optional<BehaviourProfile> latestProfile(long userId) {
        return queryOne("SELECT id,user_id,type,impulse_score,volume_score,discipline_score,source,reason,created_at " +
                "FROM behaviour_profiles WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT 1",PROFILE_MAPPER,userId);
    }
    public List<BehaviourProfile> history(long userId) {
        return queryList("SELECT id,user_id,type,impulse_score,volume_score,discipline_score,source,reason,created_at " +
                "FROM behaviour_profiles WHERE user_id=? ORDER BY created_at DESC,id DESC",PROFILE_MAPPER,userId);
    }
    /** Spending observations are read as aggregate signals; no transaction details leak into the service. */
    public SpendingSignals spendingSignals(long userId,LocalDate today) {
        LocalDate start=today.minusDays(29);
        List<LocalDate> firstRows=queryList("SELECT MIN(txn_date) first_date FROM transactions WHERE user_id=?",rs->getDate(rs,"first_date"),userId);
        LocalDate first=firstRows.isEmpty()?null:firstRows.get(0);
        long expenses=queryLong("SELECT COALESCE(SUM(t.amount),0) FROM transactions t WHERE t.user_id=? AND t.type='EXPENSE' AND t.txn_date BETWEEN ? AND ?",userId,start,today);
        long discretionary=queryLong("SELECT COALESCE(SUM(t.amount),0) FROM transactions t JOIN categories c ON c.id=t.category_id " +
                "WHERE t.user_id=? AND t.type='EXPENSE' AND c.nature='DISCRETIONARY' AND t.txn_date BETWEEN ? AND ?",userId,start,today);
        long expenseCount=queryLong("SELECT COUNT(*) FROM transactions t WHERE t.user_id=? AND t.type='EXPENSE' AND t.txn_date BETWEEN ? AND ?",userId,start,today);
        long discretionaryCount=queryLong("SELECT COUNT(*) FROM transactions t JOIN categories c ON c.id=t.category_id " +
                "WHERE t.user_id=? AND t.type='EXPENSE' AND c.nature='DISCRETIONARY' AND t.txn_date BETWEEN ? AND ?",userId,start,today);
        long purchased=queryLong("SELECT COUNT(DISTINCT p.id) FROM purchases p WHERE p.user_id=? AND p.status='PURCHASED'",userId);
        long regretted=queryLong("SELECT COUNT(DISTINCT p.id) FROM purchases p WHERE p.user_id=? AND p.status='PURCHASED' " +
                // "Ignored advice": bought although the LATEST evaluation said WAIT or AVOID.
                "AND (SELECT h.recommendation FROM purchase_history h WHERE h.purchase_id=p.id " +
                "ORDER BY h.evaluated_at DESC, h.id DESC LIMIT 1) IN ('AVOID','WAIT')",userId);
        double spendShare=expenses==0?0.0:Money.ofPaise(discretionary).ratioTo(Money.ofPaise(expenses));
        double transactionShare=expenseCount==0?0.0:(double)discretionaryCount/expenseCount;
        return new SpendingSignals(first,spendShare,transactionShare,(int)regretted,(int)purchased);
    }
}
