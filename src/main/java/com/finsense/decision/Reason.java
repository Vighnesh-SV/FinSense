package com.finsense.decision;

/** One explained reason behind a verdict, with how serious it is (used to colour the UI). */
public record Reason(Severity severity, String message) {

    /** True for reasons produced by the learning rule. */
    public boolean learned() {
        return message.startsWith("Learned from you");
    }
}
