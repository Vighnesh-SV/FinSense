package com.finsense.model;

import java.util.List;

/** Immutable answers and three normalized scores from the twelve Likert questions. */
public record QuestionnaireResult(List<Integer> answers,int impulsivityScore,int volumeScore,int disciplineScore) {
    public QuestionnaireResult { answers=List.copyOf(answers); }
}
