package com.finsense.service;

import com.finsense.dao.TransactionDAO;
import com.finsense.model.Money;
import com.finsense.model.SpendingAnomaly;
import com.finsense.model.Transaction;
import com.finsense.model.enums.TransactionType;
import com.finsense.util.SessionManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Finds unusually large expenses using a robust outlier test.
 *
 * For each recent expense, the other expenses in the same category over the past year
 * form its "normal" range. The median and the median absolute deviation (MAD) describe
 * that range; unlike the mean and standard deviation they are not pulled up by the very
 * outliers we are looking for. An expense is flagged when its modified z-score
 * 0.6745 * (x - median) / MAD exceeds 3.5 (the Iglewicz-Hoaglin rule) and it is at least
 * twice the median, so small absolute differences are never reported.
 */
public class AnomalyService {

    public static final int RECENT_DAYS = 60;
    public static final int HISTORY_DAYS = 365;
    public static final int MIN_HISTORY = 4;
    public static final double Z_THRESHOLD = 3.5;
    public static final double MIN_TIMES_MEDIAN = 2.0;

    private final TransactionDAO transactionDAO;

    public AnomalyService() {
        this(new TransactionDAO());
    }

    public AnomalyService(TransactionDAO transactionDAO) {
        this.transactionDAO = transactionDAO;
    }

    /** Unusual expenses in the last 60 days, newest first. */
    public List<SpendingAnomaly> recentAnomalies() {
        long userId = SessionManager.getInstance().requireUserId();
        LocalDate today = LocalDate.now();
        List<Transaction> expenses = transactionDAO.find(userId, today.minusDays(HISTORY_DAYS), today,
                TransactionType.EXPENSE, null, null);

        List<SpendingAnomaly> anomalies = new ArrayList<>();
        for (Transaction candidate : expenses) {
            if (candidate.date().isBefore(today.minusDays(RECENT_DAYS)) || candidate.category().commitment()) {
                continue;                                  // only recent, non-fixed spending
            }
            long[] history = expenses.stream()
                    .filter(t -> t.category().id().equals(candidate.category().id()))
                    .filter(t -> !t.id().equals(candidate.id()))
                    .mapToLong(t -> t.amount().getPaise())
                    .toArray();
            if (isAnomalous(candidate.amount().getPaise(), history)) {
                long median = median(history);
                anomalies.add(new SpendingAnomaly(candidate, Money.ofPaise(median),
                        (double) candidate.amount().getPaise() / median));
            }
        }
        return anomalies;
    }

    /** Pure function, so the statistics can be unit-tested without a database. */
    public static boolean isAnomalous(long amount, long[] history) {
        if (history.length < MIN_HISTORY) {
            return false;
        }
        long median = median(history);
        if (median <= 0 || amount < MIN_TIMES_MEDIAN * median) {
            return false;
        }
        long[] deviations = Arrays.stream(history).map(v -> Math.abs(v - median)).toArray();
        long mad = median(deviations);
        if (mad == 0) {
            return true;                                   // history is perfectly regular; 2x is unusual
        }
        double modifiedZ = 0.6745 * (amount - median) / mad;
        return modifiedZ > Z_THRESHOLD;
    }

    public static long median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
    }
}
