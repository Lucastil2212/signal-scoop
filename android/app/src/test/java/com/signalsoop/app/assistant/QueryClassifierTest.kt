package com.signalsoop.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class QueryClassifierTest {
    @Test
    fun structuredQueriesDoNotDefaultToSummary() {
        assertEquals(QueryIntent.COUNT_BLE, QueryClassifier.classify("How many BLE devices?"))
        assertEquals(QueryIntent.LIST_STRONGEST, QueryClassifier.classify("List strongest signals"))
        assertEquals(QueryIntent.ANALYZE, QueryClassifier.classify("Analyze risk and concerns"))
    }

    @Test
    fun summaryRequiresExplicitWording() {
        assertEquals(QueryIntent.SUMMARY, QueryClassifier.classify("Summarize the scan"))
        assertEquals(QueryIntent.GENERAL, QueryClassifier.classify("What is the strongest BLE device?"))
        assertEquals(QueryIntent.GENERAL, QueryClassifier.classify("Why are there so many unknown devices?"))
    }

    @Test
    fun consumerDoesNotMatchSummary() {
        assertEquals(QueryIntent.GENERAL, QueryClassifier.classify("Tell me about consumer IoT patterns"))
    }
}
