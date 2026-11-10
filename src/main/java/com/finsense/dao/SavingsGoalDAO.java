package com.finsense.dao;

import com.finsense.exception.ValidationException;
import com.finsense.model.GoalContribution;
import com.finsense.model.Money;
import com.finsense.model.SavingsGoal;
import com.finsense.model.enums.GoalPriority;
import com.finsense.model.enums.GoalStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persists goals and derives saved amounts with SQL SUM subqueries. */
public class SavingsGoalDAO extends BaseDAO {
    private static final String SELECT="SELECT g.id,g.user_id,g.name,g.target_amount,g.deadline,g.priority,g.status,"+
            "COALESCE((SELECT SUM(gc.amount) FROM goal_contributions gc WHERE gc.goal_id=g.id),0) saved_amount FROM savings_goals g ";
    private static final RowMapper<SavingsGoal> GOAL_MAPPER=rs->new SavingsGoal(rs.getLong("id"),rs.getLong("user_id"),
            rs.getString("name"),getMoney(rs,"target_amount"),getDate(rs,"deadline"),getEnum(rs,"priority",GoalPriority.class),
            getEnum(rs,"status",GoalStatus.class),getMoney(rs,"saved_amount"));
    private static final RowMapper<GoalContribution> CONTRIBUTION_MAPPER=rs->new GoalContribution(rs.getLong("id"),
            rs.getLong("goal_id"),getMoney(rs,"amount"),getDate(rs,"contributed_on"),rs.getString("note"));

    public List<SavingsGoal> findByUserId(long userId) {
        return queryList(SELECT+"WHERE g.user_id=? ORDER BY FIELD(g.status,'ACTIVE','COMPLETED','CANCELLED'),g.deadline,g.id",GOAL_MAPPER,userId);
    }
    public Optional<SavingsGoal> findById(long id,long userId) {
        return queryOne(SELECT+"WHERE g.id=? AND g.user_id=?",GOAL_MAPPER,id,userId);
    }
    public long insert(long userId,String name,Money target,LocalDate deadline,GoalPriority priority) {
        return insert("INSERT INTO savings_goals(user_id,name,target_amount,deadline,priority) VALUES(?,?,?,?,?)",
                userId,name,target,deadline,priority);
    }
    public boolean update(long id,long userId,String name,Money target,LocalDate deadline,GoalPriority priority) {
        return update("UPDATE savings_goals SET name=?,target_amount=?,deadline=?,priority=? WHERE id=? AND user_id=? AND status='ACTIVE'",
                name,target,deadline,priority,id,userId)>0;
    }
    public boolean cancel(long id,long userId) {
        return update("UPDATE savings_goals SET status='CANCELLED' WHERE id=? AND user_id=? AND status='ACTIVE'",id,userId)>0;
    }
    public List<GoalContribution> contributions(long goalId,long userId) {
        return queryList("SELECT c.id,c.goal_id,c.amount,c.contributed_on,c.note FROM goal_contributions c "+
                "JOIN savings_goals g ON g.id=c.goal_id WHERE c.goal_id=? AND g.user_id=? ORDER BY c.contributed_on DESC,c.id DESC",
                CONTRIBUTION_MAPPER,goalId,userId);
    }
    public long activeContributionsTotal(long userId) {
        return queryLong("SELECT COALESCE(SUM(c.amount),0) FROM goal_contributions c JOIN savings_goals g ON g.id=c.goal_id " +
                "WHERE g.user_id=? AND g.status='ACTIVE'",userId);
    }
    /** One JDBC transaction reserves the profile row so concurrent contributions cannot overspend free savings. */
    public long contribute(long goalId,long userId,Money amount,LocalDate date,String note) {
        return inTransaction(()->{
            boolean active=queryOne("SELECT id FROM savings_goals WHERE id=? AND user_id=? AND status='ACTIVE'",rs->rs.getLong(1),goalId,userId).isPresent();
            if(!active) throw new ValidationException("Active goal not found.");
            long totalSavings=queryLong("SELECT current_savings FROM financial_profiles WHERE user_id=? FOR UPDATE",userId);
            long committed=queryLong("SELECT COALESCE(SUM(c.amount),0) FROM goal_contributions c JOIN savings_goals g ON g.id=c.goal_id " +
                    "WHERE g.user_id=? AND g.status='ACTIVE'",userId);
            long free=Math.max(0,totalSavings-committed);
            if(amount.getPaise()>free) throw new ValidationException("Contribution exceeds free savings of "+Money.ofPaise(free).format()+".");
            long contributionId=insert("INSERT INTO goal_contributions(goal_id,amount,contributed_on,note) VALUES(?,?,?,?)",goalId,amount,date,note);
            SavingsGoal goal=findById(goalId,userId).orElseThrow();
            if(goal.savedAmount().isGreaterThan(goal.targetAmount()) || goal.savedAmount().equals(goal.targetAmount()))
                update("UPDATE savings_goals SET status='COMPLETED' WHERE id=? AND user_id=? AND status='ACTIVE'",goalId,userId);
            return contributionId;
        });
    }
}
