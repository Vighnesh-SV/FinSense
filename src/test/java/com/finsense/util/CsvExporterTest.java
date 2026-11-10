package com.finsense.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvExporterTest {
    @Test void escapesCommasAndQuotes() {
        assertEquals("\"Dining, Out\"",CsvExporter.csv("Dining, Out"));
        assertEquals("\"said \"\"hello\"\"\"",CsvExporter.csv("said \"hello\""));
        assertEquals("plain",CsvExporter.csv("plain"));
    }
}
