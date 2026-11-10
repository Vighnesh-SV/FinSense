package com.finsense.dao;

import com.finsense.model.Category;
import com.finsense.model.Money;
import com.finsense.model.Transaction;
import com.finsense.model.enums.TransactionType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** JDBC persistence for transactions, always applying user isolation. */
public class TransactionDAO extends BaseDAO {
    private static final RowMapper<Transaction> MAPPER = rs -> {
        Category category = new Category(rs.getLong("category_id"), getNullableLong(rs,"category_user_id"),
                rs.getString("category_name"), getEnum(rs,"category_type",TransactionType.class),
                rs.getString("nature"), rs.getBoolean("is_commitment"));
        return new Transaction(rs.getLong("id"),rs.getLong("user_id"),category,
                getEnum(rs,"type",TransactionType.class),getMoney(rs,"amount"),getDate(rs,"txn_date"),
                rs.getString("description"),getNullableLong(rs,"purchase_id"));
    };
    private static final String SELECT = "SELECT t.id,t.user_id,t.category_id,c.user_id category_user_id,"+
            "c.name category_name,c.type category_type,c.nature,c.is_commitment,t.type,t.amount,t.txn_date,"+
            "t.description,t.purchase_id FROM transactions t JOIN categories c ON c.id=t.category_id ";

    public List<Transaction> find(long userId, LocalDate from, LocalDate through, TransactionType type,
                                  Long categoryId, String text) {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE t.user_id=?");
        java.util.List<Object> args = new java.util.ArrayList<>(); args.add(userId);
        if (from != null) { sql.append(" AND t.txn_date>=?"); args.add(from); }
        if (through != null) { sql.append(" AND t.txn_date<=?"); args.add(through); }
        if (type != null) { sql.append(" AND t.type=?"); args.add(type); }
        if (categoryId != null) { sql.append(" AND t.category_id=?"); args.add(categoryId); }
        if (text != null && !text.isBlank()) { sql.append(" AND (t.description LIKE ? OR c.name LIKE ?)"); args.add("%"+text.trim()+"%"); args.add("%"+text.trim()+"%"); }
        sql.append(" ORDER BY t.txn_date DESC,t.id DESC");
        return queryList(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<Transaction> findById(long id,long userId) {
        return queryOne(SELECT+"WHERE t.id=? AND t.user_id=?",MAPPER,id,userId);
    }
    public long insert(long userId,long categoryId,TransactionType type,Money amount,LocalDate date,String description) {
        return insert("INSERT INTO transactions(user_id,category_id,type,amount,txn_date,description) VALUES(?,?,?,?,?,?)",
                userId,categoryId,type,amount,date,description);
    }
    public boolean update(long id,long userId,long categoryId,TransactionType type,Money amount,LocalDate date,String description) {
        return update("UPDATE transactions SET category_id=?,type=?,amount=?,txn_date=?,description=? WHERE id=? AND user_id=?",
                categoryId,type,amount,date,description,id,userId)>0;
    }
    public boolean delete(long id,long userId) {
        return update("DELETE FROM transactions WHERE id=? AND user_id=?",id,userId)>0;
    }
    public long sum(long userId,TransactionType type,LocalDate from,LocalDate through,boolean excludeCommitments) {
        String sql="SELECT COALESCE(SUM(t.amount),0) FROM transactions t JOIN categories c ON c.id=t.category_id "+
                "WHERE t.user_id=? AND t.type=? AND t.txn_date BETWEEN ? AND ?"+(excludeCommitments?" AND c.is_commitment=FALSE":"");
        return queryLong(sql,userId,type,from,through);
    }
}
