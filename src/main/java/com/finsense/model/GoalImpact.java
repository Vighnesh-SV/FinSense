package com.finsense.model;

import com.finsense.model.enums.GoalPriority;
import java.time.LocalDate;
/** Estimated goal completion dates with and without a hypothetical purchase. */
public record GoalImpact(String goalName,GoalPriority priority,LocalDate deadline,LocalDate baselineEta,
                         LocalDate purchaseEta,long delayMonths,boolean missesDeadline) { }
