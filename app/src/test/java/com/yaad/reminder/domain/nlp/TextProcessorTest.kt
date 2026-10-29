package com.yaad.reminder.domain.nlp

import com.yaad.reminder.data.local.ItemCategory
import com.yaad.reminder.domain.calendar.JalaliCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TextProcessorTest {

    @Test
    fun `parses formatted yyyy_MM_dd HH_mm string to epoch milliseconds`() {
        val epoch = TextProcessor.parseDateTimeToEpochMillis("2026/10/29 08:30")
        assertNotNull(epoch)
        val formattedBack = TextProcessor.formatEpochMillisToDateTime(epoch!!)
        assertEquals("2026/10/29 08:30", formattedBack)
    }

    @Test
    fun `parses Shamsi formatted date string and converts back to same Shamsi date`() {
        val epoch = TextProcessor.parseDateTimeToEpochMillis("1405/08/10 09:15")
        assertNotNull(epoch)
        val jalali = JalaliCalendar.fromEpochMillis(epoch!!)
        assertEquals(1405, jalali.year)
        assertEquals(8, jalali.month)
        assertEquals(10, jalali.day)
        assertEquals(9, jalali.hour)
        assertEquals(15, jalali.minute)
    }

    @Test
    fun `extracts spoken Shamsi month and day with hour`() {
        val input = "یادم باشه ۱۵ آبان ۱۴۰۵ ساعت ۱۰ صبح جلسه دارم"
        val result = TextProcessor.process(input, referenceTimeMillis = 1730000000000L)

        assertEquals(ItemCategory.REMINDER, result.category)
        assertNotNull(result.extractedTimeMillis)
        val jalali = JalaliCalendar.fromEpochMillis(result.extractedTimeMillis!!)
        assertEquals(1405, jalali.year)
        assertEquals(8, jalali.month)
        assertEquals(15, jalali.day)
        assertEquals(10, jalali.hour)
    }

    @Test
    fun `categorizes reminder and extracts Persian tomorrow and hour 8`() {
        val input = "یادم باشه فردا ساعت ۸ صبح بیدار شم"
        val result = TextProcessor.process(input, referenceTimeMillis = 1730000000000L)

        assertEquals(ItemCategory.REMINDER, result.category)
        assertNotNull(result.formattedDateTime)
        assertNotNull(result.extractedTimeMillis)
    }

    @Test
    fun `categorizes occasion keywords and extracts time`() {
        val input = "تبریک تولد سارا فردا ساعت ۶ عصر"
        val result = TextProcessor.process(input, referenceTimeMillis = 1730000000000L)

        assertEquals(ItemCategory.OCCASION, result.category)
        assertNotNull(result.extractedTimeMillis)
    }

    @Test
    fun `categorizes idea keywords without time`() {
        val input = "ایده برای طراحی اپلیکیشن مینیمال"
        val result = TextProcessor.process(input, referenceTimeMillis = 1730000000000L)

        assertEquals(ItemCategory.IDEA, result.category)
        assertNull(result.extractedTimeMillis)
    }

    @Test
    fun `falls back to UNCATEGORIZED when no keyword matches`() {
        val input = "خرید نان و پنیر"
        val result = TextProcessor.process(input, referenceTimeMillis = 1730000000000L)

        assertEquals(ItemCategory.UNCATEGORIZED, result.category)
        assertNull(result.extractedTimeMillis)
    }
}
