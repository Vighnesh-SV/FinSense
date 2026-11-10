package com.finsense.model;

import java.time.LocalDate;
/** A recurring commitment ending during the 12-month projection window. */
public record CommitmentEnding(String name,LocalDate endDate,int monthNumber,Money monthlyAmount) { }
