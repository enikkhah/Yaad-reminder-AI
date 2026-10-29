import {
  formatShamsiNumericDateTime,
  formatShamsiTimelineLabel,
  fromEpochMillisToJalali,
  jalaliMonthNumberFromName,
  jalaliToEpochMillis,
} from './jalaliCalendar';

export enum ItemCategory {
  REMINDER = 'REMINDER',
  OCCASION = 'OCCASION',
  IDEA = 'IDEA',
  UNCATEGORIZED = 'UNCATEGORIZED',
}

export interface CategoryMeta {
  id: ItemCategory;
  persianLabel: string;
  englishLabel: string;
  keywords: string[];
}

export const CATEGORY_DEFINITIONS: Record<ItemCategory, CategoryMeta> = {
  [ItemCategory.REMINDER]: {
    id: ItemCategory.REMINDER,
    persianLabel: 'یادآوری',
    englishLabel: 'REMINDER',
    keywords: ['یادم باشه', 'بیدار', 'ساعت', 'یادآوری', 'یادآور', 'زنگ', 'آلارم'],
  },
  [ItemCategory.OCCASION]: {
    id: ItemCategory.OCCASION,
    persianLabel: 'مناسبت',
    englishLabel: 'OCCASION',
    keywords: ['تولد', 'سالگرد', 'تبریک', 'مناسبت'],
  },
  [ItemCategory.IDEA]: {
    id: ItemCategory.IDEA,
    persianLabel: 'ایده',
    englishLabel: 'IDEA',
    keywords: ['ایده', 'فکر', 'طرح', 'پیشنهاد'],
  },
  [ItemCategory.UNCATEGORIZED]: {
    id: ItemCategory.UNCATEGORIZED,
    persianLabel: 'یادداشت',
    englishLabel: 'UNCATEGORIZED',
    keywords: [],
  },
};

export interface ProcessedItemResult {
  originalText: string;
  normalizedText: string;
  category: ItemCategory;
  matchedKeywords: string[];
  extractedTime: number | null;
  shamsiFormattedTime: string | null;
  matchedTimeTokens: string[];
  dayOffset: number | null;
  extractedHour: number | null;
  extractedMinute: number | null;
}

export interface ItemEntity {
  id: number;
  originalText: string;
  category: ItemCategory;
  extractedTime: number | null;
  createdAt: number;
}

const PERSIAN_DIGITS = ['۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹'];
const ARABIC_DIGITS = ['٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩'];

const PERSIAN_NUMBER_WORDS: Array<[RegExp, string]> = [
  [/(?:^|\s)پنجاه(?=\s|$)/g, ' 50 '],
  [/(?:^|\s)چهل(?=\s|$)/g, ' 40 '],
  [/(?:^|\s)سی(?=\s|$)/g, ' 30 '],
  [/(?:^|\s)بیست(?=\s|$)/g, ' 20 '],
  [/(?:^|\s)نوزده(?=\s|$)/g, ' 19 '],
  [/(?:^|\s)(?:هجده|هیجده)(?=\s|$)/g, ' 18 '],
  [/(?:^|\s)(?:هفده|هیفده)(?=\s|$)/g, ' 17 '],
  [/(?:^|\s)(?:شانزده|شونزده)(?=\s|$)/g, ' 16 '],
  [/(?:^|\s)(?:پانزده|پونزده)(?=\s|$)/g, ' 15 '],
  [/(?:^|\s)چهارده(?=\s|$)/g, ' 14 '],
  [/(?:^|\s)سیزده(?=\s|$)/g, ' 13 '],
  [/(?:^|\s)دوازده(?=\s|$)/g, ' 12 '],
  [/(?:^|\s)یازده(?=\s|$)/g, ' 11 '],
  [/(?:^|\s)ده(?=\s|$)/g, ' 10 '],
  [/(?:^|\s)نه(?=\s|$)/g, ' 9 '],
  [/(?:^|\s)هشت(?=\s|$)/g, ' 8 '],
  [/(?:^|\s)هفت(?=\s|$)/g, ' 7 '],
  [/(?:^|\s)(?:شش|شیش)(?=\s|$)/g, ' 6 '],
  [/(?:^|\s)پنج(?=\s|$)/g, ' 5 '],
  [/(?:^|\s)چهار(?=\s|$)/g, ' 4 '],
  [/(?:^|\s)سه(?=\s|$)/g, ' 3 '],
  [/(?:^|\s)دو(?=\s|$)/g, ' 2 '],
  [/(?:^|\s)(?:یک|یه)(?=\s|$)/g, ' 1 '],
];

/**
 * Converts Persian (۰-۹) and Eastern Arabic (٠-٩) digits to ASCII (0-9)
 * and standardizes Arabic Yeh/Kaf characters to Persian equivalents.
 */
export function normalizePersianInput(input: string): string {
  let result = input
    .replace(/ي/g, 'ی')
    .replace(/ك/g, 'ک')
    .replace(/\u200c/g, ' ');

  for (let i = 0; i < 10; i++) {
    result = result.replace(new RegExp(PERSIAN_DIGITS[i], 'g'), String(i));
    result = result.replace(new RegExp(ARABIC_DIGITS[i], 'g'), String(i));
  }

  result = ` ${result.replace(/\s+/g, ' ').trim()} `;
  for (const [regex, replacement] of PERSIAN_NUMBER_WORDS) {
    result = result.replace(regex, replacement);
  }
  result = result.replace(/(20|30|40|50)\s+و\s+(\d)/g, (_, tens, ones) =>
    String(Number(tens) + Number(ones))
  );

  return result.replace(/\s+/g, ' ').trim();
}

/**
 * Converts ASCII digits (0-9) into Persian digits (۰-۹) for RTL UI rendering.
 */
export function toPersianDigits(value: string | number): string {
  return String(value).replace(/\d/g, (d) => PERSIAN_DIGITS[Number(d)] ?? d);
}

/**
 * Formats an epoch millisecond timestamp into a Shamsi (Jalali) date/time label.
 */
export function formatPersianTimestamp(
  timestampMillis: number,
  referenceNowMillis: number = Date.now()
): string {
  return formatShamsiTimelineLabel(timestampMillis, referenceNowMillis);
}

export function formatPersianClockOnly(timestampMillis: number): string {
  const date = new Date(timestampMillis);
  const hh = String(date.getHours()).padStart(2, '0');
  const mm = String(date.getMinutes()).padStart(2, '0');
  return toPersianDigits(`${hh}:${mm}`);
}

/**
 * Static Rule-Based Persian NLP Processor (exact mirror of TextProcessor.kt with Shamsi Calendar).
 */
export class TextProcessor {
  private static readonly REMINDER_KEYWORDS = [
    'یادم باشه',
    'بیدار',
    'ساعت',
    'یادآوری',
    'یادآور',
    'زنگ',
    'آلارم',
  ];
  private static readonly OCCASION_KEYWORDS = ['تولد', 'سالگرد', 'تبریک', 'مناسبت'];
  private static readonly IDEA_KEYWORDS = ['ایده', 'فکر', 'طرح', 'پیشنهاد'];

  private static readonly RELATIVE_MINUTES_REGEX = /(\d{1,3})\s*دقیقه\s*(?:دیگه|دیگر|بعد)/;
  private static readonly RELATIVE_HOURS_REGEX = /(\d{1,2})\s*ساعت\s*(?:دیگه|دیگر|بعد)/;
  private static readonly RELATIVE_DAYS_REGEX = /(\d{1,2})\s*روز\s*(?:دیگه|دیگر|بعد)/;
  private static readonly HALF_HOUR_LATER_REGEX = /نیم\s*ساعت\s*(?:دیگه|دیگر|بعد)/;
  private static readonly QUARTER_HOUR_LATER_REGEX = /(?:یک|یه|1)\s*ربع\s*(?:دیگه|دیگر|بعد)/;

  // Matches Shamsi numeric date e.g. "1405/08/10"
  private static readonly SHAMSI_NUMERIC_DATE_REGEX =
    /(?:^|\s)(1[34]\d{2})[/\-](\d{1,2})[/\-](\d{1,2})(?=\s|$)/;

  // Matches Shamsi month name e.g. "10 آبان" or "25 اسفند 1405"
  private static readonly SHAMSI_NAMED_MONTH_REGEX =
    /(?:^|\s)(\d{1,2})\s*(فروردین|اردیبهشت|خرداد|تیر|مرداد|شهریور|مهر|آبان|آذر|دی|بهمن|اسفند)(?:\s*(1[34]\d{2}))?(?=\s|$)/;

  // Matches "ساعت 8", "ساعت 14:30", "ساعت 8 و نیم", "ساعت 8 شب"
  private static readonly TIME_REGEX =
    /ساعت\s*(\d{1,2})(?:\s*(?::|و)\s*(\d{1,2}|نیم|ربع))?(?:\s*دقیقه)?(?:\s*(صبح|ظهر|عصر|بعد\s*از\s*ظهر|شب|بامداد))?/;

  private static readonly STANDALONE_HOUR_REGEX =
    /(?:^|\s)(\d{1,2})(?:\s*(?::)\s*(\d{1,2}))?\s+(صبح|ظهر|عصر|بعد\s*از\s*ظهر|شب|بامداد)(?=\s|$)/;

  private static readonly DAY_OFFSET_PATTERNS: Array<{
    regex: RegExp;
    offsetDays: number;
    label: string;
  }> = [
    { regex: /(?:^|\s)هفته\s*(?:آینده|بعد|دیگه)(?=\s|$)/, offsetDays: 7, label: 'هفته آینده' },
    { regex: /(?:^|\s)پس\s*فردا(?=\s|$)/, offsetDays: 2, label: 'پس‌فردا' },
    { regex: /(?:^|\s)فردا(?=\s|$)/, offsetDays: 1, label: 'فردا' },
    { regex: /(?:^|\s)امشب(?=\s|$)/, offsetDays: 0, label: 'امشب' },
    { regex: /(?:^|\s)امروز(?=\s|$)/, offsetDays: 0, label: 'امروز' },
  ];

  public static process(rawText: string, nowMillis: number = Date.now()): ProcessedItemResult {
    const normalized = normalizePersianInput(rawText);

    const matchedTimeTokens: string[] = [];
    let dayOffset: number | null = null;
    let implicitEvening = /(?:^|\s)امشب(?=\s|$)/.test(normalized);
    let extractedHour: number | null = null;
    let extractedMinute: number | null = null;
    let extractedTime: number | null = null;

    // Parse clock time if present
    const clockMatch =
      normalized.match(this.TIME_REGEX) ?? normalized.match(this.STANDALONE_HOUR_REGEX);

    if (clockMatch) {
      matchedTimeTokens.push(clockMatch[0].trim());
      let rawHour = parseInt(clockMatch[1], 10);
      const rawMinutePart = clockMatch[2];
      const period = clockMatch[3]?.replace(/\s+/g, '');

      let minute = 0;
      if (rawMinutePart === 'نیم') {
        minute = 30;
      } else if (rawMinutePart === 'ربع') {
        minute = 15;
      } else if (rawMinutePart) {
        minute = Math.min(59, Math.max(0, parseInt(rawMinutePart, 10)));
      }

      if (period === 'عصر' || period === 'بعدازظهر' || period === 'شب') {
        if (rawHour >= 1 && rawHour < 12) rawHour += 12;
      } else if (period === 'بامداد' || period === 'صبح') {
        if (rawHour === 12) rawHour = 0;
      } else if (period === 'ظهر') {
        if (rawHour >= 1 && rawHour < 6) rawHour += 12;
      } else if (implicitEvening && rawHour >= 1 && rawHour < 12) {
        rawHour += 12;
      }

      if (rawHour >= 0 && rawHour <= 23) {
        extractedHour = rawHour;
        extractedMinute = minute;
      }
    }

    // Check relative minutes/hours first
    const halfHourMatch = normalized.match(this.HALF_HOUR_LATER_REGEX);
    const quarterHourMatch = normalized.match(this.QUARTER_HOUR_LATER_REGEX);
    const relMinutesMatch = normalized.match(this.RELATIVE_MINUTES_REGEX);
    const relHoursMatch = normalized.match(this.RELATIVE_HOURS_REGEX);

    if (halfHourMatch) {
      matchedTimeTokens.push(halfHourMatch[0].trim());
      extractedTime = nowMillis + 30 * 60 * 1000;
    } else if (quarterHourMatch) {
      matchedTimeTokens.push(quarterHourMatch[0].trim());
      extractedTime = nowMillis + 15 * 60 * 1000;
    } else if (relMinutesMatch) {
      matchedTimeTokens.push(relMinutesMatch[0].trim());
      const mins = parseInt(relMinutesMatch[1], 10) || 10;
      extractedTime = nowMillis + mins * 60 * 1000;
    } else if (relHoursMatch) {
      matchedTimeTokens.push(relHoursMatch[0].trim());
      const hrs = parseInt(relHoursMatch[1], 10) || 1;
      extractedTime = nowMillis + hrs * 60 * 60 * 1000;
    }

    // Check explicit Shamsi numeric date (e.g. 1405/08/10)
    if (extractedTime === null) {
      const shamsiNumeric = normalized.match(this.SHAMSI_NUMERIC_DATE_REGEX);
      if (shamsiNumeric) {
        matchedTimeTokens.unshift(shamsiNumeric[0].trim());
        const jy = parseInt(shamsiNumeric[1], 10);
        const jm = parseInt(shamsiNumeric[2], 10);
        const jd = parseInt(shamsiNumeric[3], 10);
        extractedTime = jalaliToEpochMillis(
          jy,
          jm,
          jd,
          extractedHour ?? 9,
          extractedHour !== null ? (extractedMinute ?? 0) : 0
        );
      }
    }

    // Check explicit Shamsi month name (e.g. "10 آبان" or "25 اسفند 1405")
    if (extractedTime === null) {
      const shamsiNamed = normalized.match(this.SHAMSI_NAMED_MONTH_REGEX);
      if (shamsiNamed) {
        matchedTimeTokens.unshift(shamsiNamed[0].trim());
        const jd = parseInt(shamsiNamed[1], 10);
        const jm = jalaliMonthNumberFromName(shamsiNamed[2]) ?? 1;
        const nowJalali = fromEpochMillisToJalali(nowMillis);
        let jy = shamsiNamed[3] ? parseInt(shamsiNamed[3], 10) : nowJalali.year;

        let targetMillis = jalaliToEpochMillis(
          jy,
          jm,
          jd,
          extractedHour ?? 9,
          extractedHour !== null ? (extractedMinute ?? 0) : 0
        );
        if (!shamsiNamed[3] && targetMillis <= nowMillis) {
          jy += 1;
          targetMillis = jalaliToEpochMillis(
            jy,
            jm,
            jd,
            extractedHour ?? 9,
            extractedHour !== null ? (extractedMinute ?? 0) : 0
          );
        }
        extractedTime = targetMillis;
      }
    }

    // Check relative day offsets
    if (extractedTime === null) {
      const relDaysMatch = normalized.match(this.RELATIVE_DAYS_REGEX);
      if (relDaysMatch) {
        dayOffset = parseInt(relDaysMatch[1], 10) || 1;
        matchedTimeTokens.unshift(relDaysMatch[0].trim());
      } else {
        for (const pattern of this.DAY_OFFSET_PATTERNS) {
          const match = normalized.match(pattern.regex);
          if (match) {
            dayOffset = pattern.offsetDays;
            matchedTimeTokens.unshift(match[0].trim());
            break;
          }
        }
      }

      if (dayOffset !== null || extractedHour !== null) {
        const targetDate = new Date(nowMillis);
        targetDate.setDate(targetDate.getDate() + (dayOffset ?? 0));

        if (extractedHour !== null) {
          targetDate.setHours(extractedHour, extractedMinute ?? 0, 0, 0);
          if (dayOffset === null && targetDate.getTime() <= nowMillis) {
            targetDate.setDate(targetDate.getDate() + 1);
          }
        } else {
          targetDate.setHours(implicitEvening ? 20 : 9, 0, 0, 0);
          if (targetDate.getTime() <= nowMillis) {
            targetDate.setDate(targetDate.getDate() + 1);
          }
        }
        extractedTime = targetDate.getTime();
      }
    }

    // Categorization
    const matchedReminder = this.REMINDER_KEYWORDS.filter((kw) => normalized.includes(kw));
    const matchedOccasion = this.OCCASION_KEYWORDS.filter((kw) => normalized.includes(kw));
    const matchedIdea = this.IDEA_KEYWORDS.filter((kw) => normalized.includes(kw));

    let category = ItemCategory.UNCATEGORIZED;
    let matchedKeywords: string[] = [];

    if (matchedOccasion.length > 0) {
      category = ItemCategory.OCCASION;
      matchedKeywords = matchedOccasion;
    } else if (matchedReminder.length > 0) {
      category = ItemCategory.REMINDER;
      matchedKeywords = matchedReminder;
    } else if (matchedIdea.length > 0) {
      category = ItemCategory.IDEA;
      matchedKeywords = matchedIdea;
    } else if (extractedTime !== null) {
      category = ItemCategory.REMINDER;
      matchedKeywords = ['زمان‌دار (شمسی)'];
    }

    if (
      extractedTime === null &&
      (category === ItemCategory.REMINDER || category === ItemCategory.OCCASION)
    ) {
      extractedTime = nowMillis + 10 * 60 * 1000;
    }

    return {
      originalText: rawText.trim(),
      normalizedText: normalized,
      category,
      matchedKeywords,
      extractedTime,
      shamsiFormattedTime:
        extractedTime !== null ? formatShamsiNumericDateTime(extractedTime) : null,
      matchedTimeTokens,
      dayOffset,
      extractedHour,
      extractedMinute,
    };
  }
}
