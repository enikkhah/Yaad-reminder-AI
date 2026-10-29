package com.yaad.reminder.domain.nlp

import com.yaad.reminder.data.local.ItemCategory
import com.yaad.reminder.data.local.ItemEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Structured output returned by [TextProcessor.process].
 */
data class ProcessedText(
    val originalText: String,
    val category: ItemCategory,
    val formattedDateTime: String?,
    val extractedTimeMillis: Long?,
    val matchedKeywords: List<String> = emptyList(),
    val matchedTimeTokens: List<String> = emptyList()
) {
    fun toEntity(createdAtMillis: Long = System.currentTimeMillis()): ItemEntity {
        val resolvedEpochMillis = formattedDateTime?.let {
            TextProcessor.parseDateTimeToEpochMillis(it)
        } ?: extractedTimeMillis

        return ItemEntity(
            originalText = originalText,
            category = category,
            extractedTime = resolvedEpochMillis,
            createdAt = createdAtMillis
        )
    }
}

/**
 * Offline, deterministic Rule-Based Persian NLP processor.
 *
 * Categorization rules:
 * - REMINDER: یادم باشه, بیدار, ساعت, یادآوری (plus spoken time/reminder phrases)
 * - OCCASION: تولد, سالگرد, تبریک, مناسبت
 * - IDEA: ایده, فکر, طرح, پیشنهاد
 * - Fallback: UNCATEGORIZED
 */
object TextProcessor {

    const val DATE_TIME_PATTERN = "yyyy/MM/dd HH:mm"

    private val REMINDER_KEYWORDS = listOf(
        "یادم باشه", "بیدار", "ساعت", "یادآوری", "یادآور", "زنگ", "آلارم"
    )
    private val OCCASION_KEYWORDS = listOf("تولد", "سالگرد", "تبریک", "مناسبت")
    private val IDEA_KEYWORDS = listOf("ایده", "فکر", "طرح", "پیشنهاد")

    // Spoken Persian number words produced by Vosk offline speech recognition
    private val PERSIAN_NUMBER_WORDS: List<Pair<Regex, String>> = listOf(
        Regex("""(?:^|\s)پنجاه(?=\s|$)""") to " 50 ",
        Regex("""(?:^|\s)چهل(?=\s|$)""") to " 40 ",
        Regex("""(?:^|\s)سی(?=\s|$)""") to " 30 ",
        Regex("""(?:^|\s)بیست(?=\s|$)""") to " 20 ",
        Regex("""(?:^|\s)نوزده(?=\s|$)""") to " 19 ",
        Regex("""(?:^|\s)(?:هجده|هیجده)(?=\s|$)""") to " 18 ",
        Regex("""(?:^|\s)(?:هفده|هیفده)(?=\s|$)""") to " 17 ",
        Regex("""(?:^|\s)(?:شانزده|شونزده)(?=\s|$)""") to " 16 ",
        Regex("""(?:^|\s)(?:پانزده|پونزده)(?=\s|$)""") to " 15 ",
        Regex("""(?:^|\s)چهارده(?=\s|$)""") to " 14 ",
        Regex("""(?:^|\s)سیزده(?=\s|$)""") to " 13 ",
        Regex("""(?:^|\s)دوازده(?=\s|$)""") to " 12 ",
        Regex("""(?:^|\s)یازده(?=\s|$)""") to " 11 ",
        Regex("""(?:^|\s)ده(?=\s|$)""") to " 10 ",
        Regex("""(?:^|\s)نه(?=\s|$)""") to " 9 ",
        Regex("""(?:^|\s)هشت(?=\s|$)""") to " 8 ",
        Regex("""(?:^|\s)هفت(?=\s|$)""") to " 7 ",
        Regex("""(?:^|\s)(?:شش|شیش)(?=\s|$)""") to " 6 ",
        Regex("""(?:^|\s)پنج(?=\s|$)""") to " 5 ",
        Regex("""(?:^|\s)چهار(?=\s|$)""") to " 4 ",
        Regex("""(?:^|\s)سه(?=\s|$)""") to " 3 ",
        Regex("""(?:^|\s)دو(?=\s|$)""") to " 2 ",
        Regex("""(?:^|\s)(?:یک|یه)(?=\s|$)""") to " 1 "
    )

    private val RELATIVE_MINUTES_REGEX = Regex("""(\d{1,3})\s*دقیقه\s*(?:دیگه|دیگر|بعد)""")
    private val RELATIVE_HOURS_REGEX = Regex("""(\d{1,2})\s*ساعت\s*(?:دیگه|دیگر|بعد)""")
    private val HALF_HOUR_LATER_REGEX = Regex("""نیم\s*ساعت\s*(?:دیگه|دیگر|بعد)""")
    private val QUARTER_HOUR_LATER_REGEX = Regex("""(?:یک|یه|1)\s*ربع\s*(?:دیگه|دیگر|بعد)""")

    private val CLOCK_TIME_REGEX = Regex(
        pattern = """ساعت\s*(\d{1,2})(?:\s*(?::|و)\s*(\d{1,2}|نیم|ربع))?(?:\s*دقیقه)?(?:\s*(صبح|ظهر|عصر|بعد\s*از\s*ظهر|شب|بامداد))?"""
    )

    private val STANDALONE_HOUR_REGEX = Regex(
        pattern = """(?:^|\s)(\d{1,2})(?:\s*:\s*(\d{1,2}))?\s+(صبح|ظهر|عصر|بعد\s*از\s*ظهر|شب|بامداد)(?=\s|$)"""
    )

    private val PAS_FARDA_REGEX = Regex("""(?:^|\s)پس\s*فردا(?=\s|$)""")
    private val FARDA_REGEX = Regex("""(?:^|\s)فردا(?=\s|$)""")
    private val EMSHAB_REGEX = Regex("""(?:^|\s)امشب(?=\s|$)""")
    private val EMROOZ_REGEX = Regex("""(?:^|\s)امروز(?=\s|$)""")

    /**
     * Helper function 1: Converts a formatted date-time string (e.g. "yyyy/MM/dd HH:mm")
     * into epoch milliseconds (Long) so [android.app.AlarmManager] can schedule it accurately.
     */
    fun parseDateTimeToEpochMillis(formattedDateTime: String): Long? {
        val cleanAscii = normalizeDigitsOnly(formattedDateTime).trim()
        if (cleanAscii.isEmpty()) return null
        return runCatching {
            val parser = SimpleDateFormat(DATE_TIME_PATTERN, Locale.US).apply {
                isLenient = false
            }
            parser.parse(cleanAscii)?.time
        }.getOrNull()
    }

    /**
     * Helper function 2: Formats epoch milliseconds (Long) into "yyyy/MM/dd HH:mm".
     */
    fun formatEpochMillisToDateTime(epochMillis: Long): String {
        val formatter = SimpleDateFormat(DATE_TIME_PATTERN, Locale.US)
        return formatter.format(Date(epochMillis))
    }

    /**
     * Classifies raw Persian text and extracts relative time/date into both "yyyy/MM/dd HH:mm"
     * and epoch milliseconds (Long).
     */
    fun process(
        rawInput: String,
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): ProcessedText {
        val cleanedOriginal = rawInput.trim()
        val normalized = normalizePersianText(cleanedOriginal)

        val timeExtraction = extractTimestamp(normalized, referenceTimeMillis)
        val rawMillis = timeExtraction.first
        val formattedTime = rawMillis?.let { formatEpochMillisToDateTime(it) }
        val convertedEpochMillis = formattedTime?.let { parseDateTimeToEpochMillis(it) } ?: rawMillis

        val reminderHits = REMINDER_KEYWORDS.filter { normalized.contains(it) }
        val occasionHits = OCCASION_KEYWORDS.filter { normalized.contains(it) }
        val ideaHits = IDEA_KEYWORDS.filter { normalized.contains(it) }

        val (category, matchedKeywords) = when {
            occasionHits.isNotEmpty() -> ItemCategory.OCCASION to occasionHits
            reminderHits.isNotEmpty() -> ItemCategory.REMINDER to reminderHits
            ideaHits.isNotEmpty() -> ItemCategory.IDEA to ideaHits
            convertedEpochMillis != null -> ItemCategory.REMINDER to listOf("زمان‌دار")
            else -> ItemCategory.UNCATEGORIZED to emptyList()
        }

        // If categorized as REMINDER or OCCASION without an explicit clock time, assign 10 minutes ahead
        // so that every reminder request triggers an actual system alarm.
        val finalEpochMillis = if (convertedEpochMillis == null &&
            (category == ItemCategory.REMINDER || category == ItemCategory.OCCASION)
        ) {
            val defaultFormatted = formatEpochMillisToDateTime(referenceTimeMillis + 10 * 60 * 1000L)
            parseDateTimeToEpochMillis(defaultFormatted)
        } else {
            convertedEpochMillis
        }

        val finalFormatted = finalEpochMillis?.let { formatEpochMillisToDateTime(it) }

        return ProcessedText(
            originalText = cleanedOriginal,
            category = category,
            formattedDateTime = finalFormatted,
            extractedTimeMillis = finalEpochMillis,
            matchedKeywords = matchedKeywords,
            matchedTimeTokens = timeExtraction.second
        )
    }

    private fun extractTimestamp(
        normalizedText: String,
        referenceTimeMillis: Long
    ): Pair<Long?, List<String>> {
        val matchedTokens = mutableListOf<String>()

        HALF_HOUR_LATER_REGEX.find(normalizedText)?.let { match ->
            matchedTokens += match.value.trim()
            return (referenceTimeMillis + 30 * 60 * 1000L) to matchedTokens
        }

        QUARTER_HOUR_LATER_REGEX.find(normalizedText)?.let { match ->
            matchedTokens += match.value.trim()
            return (referenceTimeMillis + 15 * 60 * 1000L) to matchedTokens
        }

        RELATIVE_MINUTES_REGEX.find(normalizedText)?.let { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: 10L
            matchedTokens += match.value.trim()
            return (referenceTimeMillis + minutes * 60 * 1000L) to matchedTokens
        }

        RELATIVE_HOURS_REGEX.find(normalizedText)?.let { match ->
            val hours = match.groupValues[1].toLongOrNull() ?: 1L
            matchedTokens += match.value.trim()
            return (referenceTimeMillis + hours * 60 * 60 * 1000L) to matchedTokens
        }

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
                minuteToken == "ربع" -> 15
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

                if (dayOffset == null && timeInMillis <= referenceTimeMillis) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            } else {
                set(Calendar.HOUR_OF_DAY, if (implicitEvening) 20 else 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= referenceTimeMillis) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }

        return calendar.timeInMillis to matchedTokens
    }

    private fun normalizeDigitsOnly(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '۰'..'۹' -> sb.append((ch - '۰' + '0'.code).toChar())
                in '٠'..'٩' -> sb.append((ch - '٠' + '0'.code).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

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
        var normalized = " ${sb.toString().replace(Regex("""\s+"""), " ").trim()} "
        for ((wordRegex, digitReplacement) in PERSIAN_NUMBER_WORDS) {
            normalized = wordRegex.replace(normalized, digitReplacement)
        }
        normalized = Regex("""(20|30|40|50)\s+و\s+(\d)""").replace(normalized) { m ->
            val tens = m.groupValues[1].toInt()
            val ones = m.groupValues[2].toInt()
            (tens + ones).toString()
        }
        return normalized.replace(Regex("""\s+"""), " ").trim()
    }
}
