package com.finsense;

/**
 * Entry point of FinSense.
 *
 * Why a separate launcher class?
 * When JavaFX jars are on the classpath (not the module path), starting a class
 * that directly extends javafx.application.Application fails with
 * "JavaFX runtime components are missing". Starting from a plain class that
 * merely delegates to the Application subclass avoids this, so the app runs the
 * same way from Maven, IntelliJ, VS Code / Antigravity, or a jar.
 */
public final class Main {

    private Main() {
        // utility launcher - not meant to be instantiated
    }

    public static void main(String[] args) {
        FinSenseApp.main(args);
    }
}
