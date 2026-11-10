package com.finsense.decision;

import com.finsense.exception.ValidationException;
import com.finsense.model.enums.BehaviourType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QuestionnaireClassifierTest {
    private final QuestionnaireClassifier classifier=new QuestionnaireClassifier();
    @Test void classificationHonoursTheSixtyPointBoundaries() {
        assertEquals(BehaviourType.IMPULSE_BUYER,classifier.classify(60,60,40));
        assertEquals(BehaviourType.HEAVY_BUYER,classifier.classify(59,60,70));
        assertEquals(BehaviourType.SMART_SHOPPER,classifier.classify(59,59,80));
    }
    @Test void decisionMakingAnswersAreReverseScored() {
        var result=classifier.score(List.of(1,1,5,5,1,1,1,1,5,5,5,5));
        assertEquals(0,result.impulsivityScore()); assertEquals(0,result.volumeScore()); assertEquals(100,result.disciplineScore());
        var reversed=classifier.score(List.of(5,5,1,1,5,5,5,5,1,1,1,1));
        assertEquals(100,reversed.impulsivityScore());
    }
    @Test void answerCountAndLikertRangeAreValidated() {
        assertThrows(ValidationException.class,()->classifier.score(List.of(1,2,3)));
        assertThrows(ValidationException.class,()->classifier.score(List.of(1,1,1,1,1,1,1,1,1,1,1,6)));
    }
}
