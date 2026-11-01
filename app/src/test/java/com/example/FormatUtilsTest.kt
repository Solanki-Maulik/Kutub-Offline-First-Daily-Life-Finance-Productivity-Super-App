package com.example

import com.example.utils.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun testIndianCurrencyFormatting() {
        // Standard hundreds and thousands
        val formatted1k = FormatUtils.formatInr(1000.0)
        assertTrue(formatted1k.contains("1,000"))

        // Lakhs (1,00,000)
        val formatted1Lakh = FormatUtils.formatInr(100000.0)
        assertTrue(formatted1Lakh.contains("1,00,000"))

        // Multi-lakhs (12,34,567)
        val formattedMultiLakh = FormatUtils.formatInr(1234567.0)
        assertTrue(formattedMultiLakh.contains("12,34,567"))
    }

    @Test
    fun testDateFormattingAndParsing() {
        // Test parsing valid DD/MM/YYYY
        val millis = FormatUtils.parseDate("24/10/2026")
        assertNotNull(millis)

        // Test formatting back to DD/MM/YYYY
        val dateString = FormatUtils.formatDate(millis!!)
        assertEquals("24/10/2026", dateString)

        // Test invalid date string
        val invalidMillis = FormatUtils.parseDate("invalid-date")
        assertNull(invalidMillis)
    }
}
