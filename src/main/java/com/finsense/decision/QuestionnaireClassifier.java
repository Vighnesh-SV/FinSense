package com.finsense.decision;

import com.finsense.exception.ValidationException;
import com.finsense.model.QuestionnaireResult;
import com.finsense.model.enums.BehaviourType;
import java.util.List;

/** Scores twelve Likert answers and classifies them through the BehaviourClassifier strategy. */
public final class QuestionnaireClassifier implements BehaviourClassifier {
    @Override public BehaviourType classify(int impulsivity,int volume,int discipline) {
        if(impulsivity>=60 && impulsivity>=volume) return BehaviourType.IMPULSE_BUYER;
        if(volume>=60) return BehaviourType.HEAVY_BUYER;
        return BehaviourType.SMART_SHOPPER;
    }
    /** Each answer is scaled 1..5 to 0..100; decision-making answers are reverse-scored. */
    public QuestionnaireResult score(List<Integer> answers) {
        if(answers==null || answers.size()!=12 || answers.stream().anyMatch(a->a==null || a<1 || a>5))
            throw new ValidationException("Answer all 12 questions using a value from 1 to 5.");
        int impulse=average(scale(answers.get(0)),scale(answers.get(1)),100-scale(answers.get(2)),100-scale(answers.get(3)));
        int volume=average(scale(answers.get(4)),scale(answers.get(5)),scale(answers.get(6)),scale(answers.get(7)));
        int discipline=average(scale(answers.get(8)),scale(answers.get(9)),scale(answers.get(10)),scale(answers.get(11)));
        return new QuestionnaireResult(answers,impulse,volume,discipline);
    }
    public static int scale(int answer) { return (answer-1)*25; }
    private static int average(int... scores) { int sum=0;for(int score:scores)sum+=score;return (sum+scores.length/2)/scores.length; }
}
