package com.finsense.decision;

import com.finsense.model.Money;
import com.finsense.model.enums.GoalPriority;
import java.time.LocalDate;

/** Minimum immutable goal data needed by the pure evaluator. */
public record GoalSnapshot(Money remainingAmount,LocalDate deadline,GoalPriority priority) { }
