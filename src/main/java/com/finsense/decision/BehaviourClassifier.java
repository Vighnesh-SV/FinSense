package com.finsense.decision;

import com.finsense.model.enums.BehaviourType;

/** Strategy interface: multiple classification sources share the same type decision contract. */
public interface BehaviourClassifier {
    BehaviourType classify(int impulsivity,int volume,int discipline);
}
