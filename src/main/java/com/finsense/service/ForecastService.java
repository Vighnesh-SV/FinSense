package com.finsense.service;

import com.finsense.dao.TransactionDAO;
import com.finsense.dao.TransactionDAO.CategoryMonthTotal;
import com.finsense.model.Category;
import com.finsense.model.CategoryForecast;
import com.finsense.model.Money;
import com.finsense.util.SessionManager;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Predicts next month's spending per category from the last six complete months.
 *
 * With three or more months of data it fits a straight line through the monthly totals
 * using ordinary least squares (linear regression), so a category that is steadily
 * rising or falling is projected forward. With one or two months it uses the average.
 * The result is clamped so a short spike cannot predict a negative or absurd value.
 */
public class ForecastService {

    public static final int HISTORY_MONTHS = 6;
    public static final int MIN_MONTHS_FOR_TREND = 3;

    private final TransactionDAO transactionDAO;

    public ForecastService() {
        this(new TransactionDAO());
    }

    public ForecastService(TransactionDAO transactionDAO) {
        this.transactionDAO = transactionDAO;
    }

    /** Forecast for every category the user spent in recently, largest first. */
    public List<CategoryForecast> forecastNextMonth() {
        long userId = SessionManager.getInstance().requireUserId();
        Optional<LocalDate> first = transactionDAO.firstTransactionDate(userId);
        YearMonth lastComplete = YearMonth.now().minusMonths(1);
        if (first.isEmpty() || !YearMonth.from(first.get()).isBefore(YearMonth.now())) {
            return List.of();                                  // no complete month yet
        }
        YearMonth start = lastComplete.minusMonths(HISTORY_MONTHS - 1L);
        if (YearMonth.from(first.get()).isAfter(start)) {
            start = YearMonth.from(first.get());
        }
        int months = (int) ChronoUnit.MONTHS.between(start, lastComplete) + 1;

        // Group the SQL rows into one series per category (missing months stay 0).
        Map<Long, Category> categories = new LinkedHashMap<>();
        Map<Long, long[]> series = new LinkedHashMap<>();
        for (CategoryMonthTotal row : transactionDAO.monthlyExpenseTotals(
                userId, start.atDay(1), lastComplete.atEndOfMonth())) {
            long id = row.category().id();
            categories.putIfAbsent(id, row.category());
            long[] values = series.computeIfAbsent(id, k -> new long[months]);
            values[(int) ChronoUnit.MONTHS.between(start, row.month())] = row.total().getPaise();
        }

        List<CategoryForecast> result = new ArrayList<>();
        series.forEach((id, values) -> {
            boolean trend = values.length >= MIN_MONTHS_FOR_TREND;
            long predicted = trend ? linearForecast(values) : average(values);
            result.add(new CategoryForecast(categories.get(id), Money.ofPaise(values[values.length - 1]),
                    Money.ofPaise(predicted), trend ? "Linear trend" : "Average", values.length));
        });
        result.sort(Comparator.comparingLong((CategoryForecast f) -> f.forecast().getPaise()).reversed());
        return result;
    }

    /** Sum of all category forecasts - next month's expected non-commitment spending. */
    public Money totalForecast() {
        return forecastNextMonth().stream().map(CategoryForecast::forecast).reduce(Money.ZERO, Money::plus);
    }

    /**
     * Least-squares line y = a + b*x through (0, y0) ... (n-1, yn-1), evaluated at x = n.
     * b = sum((x - meanX)(y - meanY)) / sum((x - meanX)^2), a = meanY - b*meanX.
     * The prediction is kept between 0 and twice the largest month seen.
     */
    public static long linearForecast(long[] values) {
        int n = values.length;
        double meanX = (n - 1) / 2.0;
        double meanY = average(values);
        double numerator = 0;
        double denominator = 0;
        long max = 0;
        for (int x = 0; x < n; x++) {
            numerator += (x - meanX) * (values[x] - meanY);
            denominator += (x - meanX) * (x - meanX);
            max = Math.max(max, values[x]);
        }
        double slope = denominator == 0 ? 0 : numerator / denominator;
        double prediction = meanY + slope * (n - meanX);
        return Math.round(Math.max(0, Math.min(prediction, 2.0 * max)));
    }

    public static long average(long[] values) {
        long total = 0;
        for (long value : values) {
            total += value;
        }
        return values.length == 0 ? 0 : Math.round((double) total / values.length);
    }
}
