package com.yaad.reminder.domain.nlp

import com.yaad.reminder.data.local.ItemCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TextProcessorTest {

    @Test
    fun `categorizes reminder and extracts Persian tomorrow and hour 8`() {
        val input = "یادم باشه فردا ساعت ۸ صبح بیدار شم"
        val result = TextProcessor.process(input, referenceTimeMillis = 1730000000000L)

        assertEquals(ItemCategory.REMINDER, result.category)
        assertNotNull(result.extractedTimeMillis)
    }

    @Test
    fun `categorizes occasion keywords`() {
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
