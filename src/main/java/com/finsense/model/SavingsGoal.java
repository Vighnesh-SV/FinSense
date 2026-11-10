package com.finsense.model;

import com.finsense.model.enums.GoalPriority;
import com.finsense.model.enums.GoalStatus;
import java.time.LocalDate;

/** Goal summary with savedAmount derived from contribution rows, never stored twice. */
public record SavingsGoal(Long id,long userId,String name,Money targetAmount,LocalDate deadline,
                          GoalPriority priority,GoalStatus status,Money savedAmount) {
    public Money remainingAmount() { return Money.max(Money.ZERO,targetAmount.minus(savedAmount)); }
    public double progressPercent() {
        if(targetAmount.isZero()) return 0;
        return Math.min(100.0,savedAmount.ratioTo(targetAmount)*100.0);
    }
}
