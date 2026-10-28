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
    keywords: ['یادم باشه', 'بیدار', 'ساعت', 'یادآوری'],
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

  return result.replace(/\s+/g, ' ').trim();
}

/**
 * Converts ASCII digits (0-9) into Persian digits (۰-۹) for RTL UI rendering.
 */
export function toPersianDigits(value: string | number): string {
  return String(value).replace(/\d/g, (d) => PERSIAN_DIGITS[Number(d)] ?? d);
}

/**
 * Formats an epoch millisecond timestamp into a concise Persian date/time label.
 */
export function formatPersianTimestamp(
  timestampMillis: number,
  referenceNowMillis: number = Date.now()
): string {
  const target = new Date(timestampMillis);
  const now = new Date(referenceNowMillis);

  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const startOfTarget = new Date(
    target.getFullYear(),
    target.getMonth(),
    target.getDate()
  ).getTime();

  const diffDays = Math.round((startOfTarget - startOfToday) / (1000 * 60 * 60 * 24));
  const hh = String(target.getHours()).padStart(2, '0');
  const mm = String(target.getMinutes()).padStart(2, '0');
  const timeStr = toPersianDigits(`${hh}:${mm}`);

  if (diffDays === 0) {
    return `امروز ${timeStr}`;
  }
  if (diffDays === 1) {
    return `فردا ${timeStr}`;
  }
  if (diffDays === 2) {
    return `پس‌فردا ${timeStr}`;
  }

  try {
    const datePart = new Intl.DateTimeFormat('fa-IR', {
      month: 'short',
      day: 'numeric',
    }).format(target);
    return `${datePart} ساعت ${timeStr}`;
  } catch {
    return timeStr;
  }
}

export function formatPersianClockOnly(timestampMillis: number): string {
  const date = new Date(timestampMillis);
  const hh = String(date.getHours()).padStart(2, '0');
  const mm = String(date.getMinutes()).padStart(2, '0');
  return toPersianDigits(`${hh}:${mm}`);
}

/**
 * Static Rule-Based Persian NLP Processor (exact mirror of TextProcessor.kt).
 * - Categorizes input into REMINDER, OCCASION, IDEA, or UNCATEGORIZED.
 * - Extracts relative Persian date/time expressions via Regex into epoch milliseconds.
 */
export class TextProcessor {
  private static readonly REMINDER_KEYWORDS = ['یادم باشه', 'بیدار', 'ساعت', 'یادآوری'];
  private static readonly OCCASION_KEYWORDS = ['تولد', 'سالگرد', 'تبریک', 'مناسبت'];
  private static readonly IDEA_KEYWORDS = ['ایده', 'فکر', 'طرح', 'پیشنهاد'];

  // Matches "ساعت 8", "ساعت 14:30", "ساعت 8 و نیم", "ساعت 8 شب"
  private static readonly TIME_REGEX =
    /ساعت\s*(\d{1,2})(?:\s*(?::|و)\s*(\d{1,2}|نیم))?(?:\s*(صبح|ظهر|عصر|بعد\s*از\s*ظهر|شب|بامداد))?/;

  // Fallback for standalone hour + period like "8 صبح" or "10 شب" without "ساعت"
  private static readonly STANDALONE_HOUR_REGEX =
    /(?:^|\s)(\d{1,2})(?:\s*(?::)\s*(\d{1,2}))?\s+(صبح|ظهر|عصر|بعد\s*از\s*ظهر|شب|بامداد)(?=\s|$)/;

  private static readonly DAY_OFFSET_PATTERNS: Array<{ regex: RegExp; offsetDays: number; label: string }> = [
    { regex: /(?:^|\s)پس\s*فردا(?=\s|$)/, offsetDays: 2, label: 'پس‌فردا' },
    { regex: /(?:^|\s)فردا(?=\s|$)/, offsetDays: 1, label: 'فردا' },
    { regex: /(?:^|\s)امشب(?=\s|$)/, offsetDays: 0, label: 'امشب' },
    { regex: /(?:^|\s)امروز(?=\s|$)/, offsetDays: 0, label: 'امروز' },
  ];

  public static process(rawText: string, nowMillis: number = Date.now()): ProcessedItemResult {
    const normalized = normalizePersianInput(rawText);

    // 1. Static keyword categorization (priority order: REMINDER -> OCCASION -> IDEA -> UNCATEGORIZED)
    const matchedReminder = this.REMINDER_KEYWORDS.filter((kw) => normalized.includes(kw));
    const matchedOccasion = this.OCCASION_KEYWORDS.filter((kw) => normalized.includes(kw));
    const matchedIdea = this.IDEA_KEYWORDS.filter((kw) => normalized.includes(kw));

    let category = ItemCategory.UNCATEGORIZED;
    let matchedKeywords: string[] = [];

    if (matchedReminder.length > 0) {
      category = ItemCategory.REMINDER;
      matchedKeywords = matchedReminder;
    } else if (matchedOccasion.length > 0) {
      category = ItemCategory.OCCASION;
      matchedKeywords = matchedOccasion;
    } else if (matchedIdea.length > 0) {
      category = ItemCategory.IDEA;
      matchedKeywords = matchedIdea;
    }

    // 2. Persian Regex Time & Date Extraction
    const matchedTimeTokens: string[] = [];
    let dayOffset: number | null = null;
    let implicitEvening = false;

    for (const pattern of this.DAY_OFFSET_PATTERNS) {
      const match = normalized.match(pattern.regex);
      if (match) {
        dayOffset = pattern.offsetDays;
        matchedTimeTokens.push(match[0].trim());
        if (pattern.label === 'امشب') {
          implicitEvening = true;
        }
        break;
      }
    }

    let extractedHour: number | null = null;
    let extractedMinute: number | null = null;

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
      } else if (rawMinutePart) {
        minute = Math.min(59, Math.max(0, parseInt(rawMinutePart, 10)));
      }

      if (period === 'عصر' || period === 'بعدازظهر' || period === 'شب') {
        if (rawHour >= 1 && rawHour < 12) {
          rawHour += 12;
        }
      } else if (period === 'بامداد' || period === 'صبح') {
        if (rawHour === 12) {
          rawHour = 0;
        }
      } else if (period === 'ظهر') {
        if (rawHour >= 1 && rawHour < 6) {
          rawHour += 12;
        }
      } else if (implicitEvening && rawHour >= 1 && rawHour < 12) {
        rawHour += 12;
      }

      if (rawHour >= 0 && rawHour <= 23) {
        extractedHour = rawHour;
        extractedMinute = minute;
      }
    }

    // Compute epoch timestamp if either a day offset or clock hour was matched
    let extractedTime: number | null = null;
    if (dayOffset !== null || extractedHour !== null) {
      const targetDate = new Date(nowMillis);
      const effectiveDayOffset = dayOffset ?? 0;
      targetDate.setDate(targetDate.getDate() + effectiveDayOffset);

      if (extractedHour !== null) {
        targetDate.setHours(extractedHour, extractedMinute ?? 0, 0, 0);
        // If user only said "ساعت ۸" without a day word and that hour already passed today, advance to tomorrow
        if (dayOffset === null && targetDate.getTime() <= nowMillis) {
          targetDate.setDate(targetDate.getDate() + 1);
        }
      } else {
        // Default hour when only "فردا" or "امشب" is mentioned without explicit clock hour
        targetDate.setHours(implicitEvening ? 20 : 9, 0, 0, 0);
      }

      extractedTime = targetDate.getTime();
    }

    return {
      originalText: rawText.trim(),
      normalizedText: normalized,
      category,
      matchedKeywords,
      extractedTime,
      matchedTimeTokens,
      dayOffset,
      extractedHour,
      extractedMinute,
    };
  }
}
