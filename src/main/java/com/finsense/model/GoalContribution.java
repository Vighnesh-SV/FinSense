package com.finsense.model;
import java.time.LocalDate;
/** Immutable contribution event; its amount is included in a goal's derived total. */
public record GoalContribution(Long id,long goalId,Money amount,LocalDate contributedOn,String note) { }
