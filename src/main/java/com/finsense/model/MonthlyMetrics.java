package com.finsense.model;

import java.time.YearMonth;
/** Income and expense totals for a chart month. */
public record MonthlyMetrics(YearMonth month,Money income,Money expenses) { }
