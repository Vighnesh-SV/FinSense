package com.finsense.model;

import com.finsense.model.enums.BehaviourType;
import com.finsense.model.enums.BehaviourSource;
import java.time.LocalDateTime;

/** One row of behaviour classification history; the newest row is the current type. */
public record BehaviourProfile(Long id,long userId,BehaviourType type,int impulsivityScore,int volumeScore,
                               int disciplineScore,BehaviourSource source,String reason,LocalDateTime createdAt) { }
