package com.finsense.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The monthly report must never let user-typed text break (or inject into) the HTML. */
class ReportServiceTest {

    @Test
    void escapesHtmlSpecialCharacters() {
        assertEquals("&lt;b&gt;Tom &amp; Jerry&#39;s &quot;TV&quot;&lt;/b&gt;",
                ReportService.escape("<b>Tom & Jerry's \"TV\"</b>"));
    }

    @Test
    void nullBecomesEmpty() {
        assertEquals("", ReportService.escape(null));
    }
}
