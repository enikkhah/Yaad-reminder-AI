package com.yaad.reminder.domain.nlp

import com.yaad.reminder.data.local.ItemCategory
import com.yaad.reminder.data.local.ItemEntity
import java.util.Calendar

/**
 * Structured output returned by [TextProcessor.process].
 */
data class ProcessedText(
    val originalText: String,
    val category: ItemCategory,
    val extractedTimeMillis: Long?,
    val matchedKeywords: List<String> = emptyList(),
    val matchedTimeTokens: List<String> = emptyList()
) {
    fun toEntity(createdAtMillis: Long = System.currentTimeMillis()): ItemEntity {
        return ItemEntity(
            originalText = originalText,
            category = category,
            extractedTime = extractedTimeMillis,
            createdAt = createdAtMillis
        )
    }
}

/**
 * Offline, deterministic Rule-Based Persian NLP processor.
 *
 * Categorization rules:
 * - REMINDER: یادم باشه, بیدار, ساعت, یادآوری
 * - OCCASION: تولد, سالگرد, تبریک, مناسبت
 * - IDEA: ایده, فکر, طرح, پیشنهاد
 * - Fallback: UNCATEGORIZED
 */
object TextProcessor {

    private val REMINDER_KEYWORDS = listOf("یادم باشه", "بیدار", "ساعت", "یادآوری")
    private val OCCASION_KEYWORDS = listOf("تولد", "سالگرد", "تبریک", "مناسبت")
    private val IDEA_KEYWORDS = listOf("ایده", "فکر", "طرح", "پیشنهاد")

    // Matches Persian clock patterns such as "ساعت ۸", "ساعت ۱۴:۳۰", "ساعت ۸ و نیم", "ساعت ۹ شب"
    private val CLOCK_TIME_REGEX = Regex(
        pattern = """ساعت\s*(\d{1,2})(?:\s*(?::|و)\s*(\d{1,2}|نیم))?(?:\s*(صبح|ظهر|عصر|بعد\s*از\s*ظهر|شب|بامداد))?"""
    )

    // Matches standalone hour + period when "ساعت" is omitted, e.g. "۸ صبح" or "۱۰ شب"
    private val STANDALONE_HOUR_REGEX = Regex(
        pattern = """(?:^|\s)(\d{1,2})(?:\s*:\s*(\d{1,2}))?\s+(صبح|ظهر|عصر|بعد\s*از\s*ظهر|شب|بامداد)(?=\s|$)"""
    )

    private val PAS_FARDA_REGEX = Regex("""(?:^|\s)پس\s*فردا(?=\s|$)""")
    private val FARDA_REGEX = Regex("""(?:^|\s)فردا(?=\s|$)""")
    private val EMSHAB_REGEX = Regex("""(?:^|\s)امشب(?=\s|$)""")
    private val EMROOZ_REGEX = Regex("""(?:^|\s)امروز(?=\s|$)""")

    /**
     * Classifies raw Persian text and extracts relative time/date into epoch milliseconds.
     */
    fun process(
        rawInput: String,
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): ProcessedText {
        val cleanedOriginal = rawInput.trim()
        val normalized = normalizePersianText(cleanedOriginal)

        // 1. Categorize using static keyword lists
        val reminderHits = REMINDER_KEYWORDS.filter { normalized.contains(it) }
        val occasionHits = OCCASION_KEYWORDS.filter { normalized.contains(it) }
        val ideaHits = IDEA_KEYWORDS.filter { normalized.contains(it) }

        val (category, matchedKeywords) = when {
            reminderHits.isNotEmpty() -> ItemCategory.REMINDER to reminderHits
            occasionHits.isNotEmpty() -> ItemCategory.OCCASION to occasionHits
            ideaHits.isNotEmpty() -> ItemCategory.IDEA to ideaHits
            else -> ItemCategory.UNCATEGORIZED to emptyList()
        }

        // 2. Extract Persian relative date & clock time via Regex
        val timeExtraction = extractTimestamp(normalized, referenceTimeMillis)

        return ProcessedText(
            originalText = cleanedOriginal,
            category = category,
            extractedTimeMillis = timeExtraction.first,
            matchedKeywords = matchedKeywords,
            matchedTimeTokens = timeExtraction.second
        )
    }

    private fun extractTimestamp(
        normalizedText: String,
        referenceTimeMillis: Long
    ): Pair<Long?, List<String>> {
        val matchedTokens = mutableListOf<String>()
        var dayOffset: Int? = null
        var implicitEvening = false

        when {
            PAS_FARDA_REGEX.containsMatchIn(normalizedText) -> {
                dayOffset = 2
                matchedTokens += "پس‌فردا"
            }
            FARDA_REGEX.containsMatchIn(normalizedText) -> {
                dayOffset = 1
                matchedTokens += "فردا"
            }
            EMSHAB_REGEX.containsMatchIn(normalizedText) -> {
                dayOffset = 0
                implicitEvening = true
                matchedTokens += "امشب"
            }
            EMROOZ_REGEX.containsMatchIn(normalizedText) -> {
                dayOffset = 0
                matchedTokens += "امروز"
            }
        }

        val clockMatch = CLOCK_TIME_REGEX.find(normalizedText)
            ?: STANDALONE_HOUR_REGEX.find(normalizedText)

        var parsedHour: Int? = null
        var parsedMinute = 0

        if (clockMatch != null) {
            matchedTokens += clockMatch.value.trim()
            var hour = clockMatch.groupValues[1].toIntOrNull() ?: 0
            val minuteToken = clockMatch.groupValues[2]
            val meridiem = clockMatch.groupValues[3].replace(" ", "")

            parsedMinute = when {
                minuteToken == "نیم" -> 30
                minuteToken.isNotEmpty() -> (minuteToken.toIntOrNull() ?: 0).coerceIn(0, 59)
                else -> 0
            }

            when (meridiem) {
                "عصر", "بعدازظهر", "شب" -> if (hour in 1..11) hour += 12
                "صبح", "بامداد" -> if (hour == 12) hour = 0
                "ظهر" -> if (hour in 1..5) hour += 12
                else -> if (implicitEvening && hour in 1..11) hour += 12
            }

            if (hour in 0..23) {
                parsedHour = hour
            }
        }

        if (dayOffset == null && parsedHour == null) {
            return null to emptyList()
        }

        val calendar = Calendar.getInstance().apply {
            timeInMillis = referenceTimeMillis
            add(Calendar.DAY_OF_YEAR, dayOffset ?: 0)

            if (parsedHour != null) {
                set(Calendar.HOUR_OF_DAY, parsedHour)
                set(Calendar.MINUTE, parsedMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                // If only "ساعت X" was spoken and that time has already passed today, schedule for tomorrow
                if (dayOffset == null && timeInMillis <= referenceTimeMillis) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            } else {
                // Default morning (09:00) or evening (20:00 for "امشب") when no explicit hour is given
                set(Calendar.HOUR_OF_DAY, if (implicitEvening) 20 else 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }

        return calendar.timeInMillis to matchedTokens
    }

    /**
     * Normalizes Persian (۰-۹) and Eastern Arabic (٠-٩) digits to ASCII (0-9),
     * unifies Arabic Yeh/Kaf to Persian, and replaces ZWNJ with standard whitespace.
     */
    fun normalizePersianText(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '۰'..'۹' -> sb.append((ch - '۰' + '0'.code).toChar())
                in '٠'..'٩' -> sb.append((ch - '٠' + '0'.code).toChar())
                'ي' -> sb.append('ی')
                'ك' -> sb.append('ک')
                '\u200C' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace(Regex("""\s+"""), " ").trim()
    }
}
