import { toPersianDigits } from './textProcessor';

export interface JalaliDateTime {
  year: number;
  month: number; // 1..12
  day: number; // 1..31
  hour: number;
  minute: number;
  dayOfWeek: number; // 0 (Sunday) .. 6 (Saturday)
  monthName: string;
  weekdayName: string;
}

export const JALALI_MONTH_NAMES = [
  'فروردین',
  'اردیبهشت',
  'خرداد',
  'تیر',
  'مرداد',
  'شهریور',
  'مهر',
  'آبان',
  'آذر',
  'دی',
  'بهمن',
  'اسفند',
];

export const PERSIAN_WEEKDAYS = [
  'یکشنبه',
  'دوشنبه',
  'سه‌شنبه',
  'چهارشنبه',
  'پنجشنبه',
  'جمعه',
  'شنبه',
];

export function jalaliMonthName(month: number): string {
  return JALALI_MONTH_NAMES[Math.max(0, Math.min(11, month - 1))] ?? 'فروردین';
}

export function jalaliMonthNumberFromName(name: string): number | null {
  const clean = name.trim().replace(/\u200c/g, ' ');
  const idx = JALALI_MONTH_NAMES.findIndex((m) => m === clean);
  return idx >= 0 ? idx + 1 : null;
}

export function isLeapJalaliYear(jalaliYear: number): boolean {
  const r = (jalaliYear + 2346) % 2820;
  const mod = ((r % 128) * 31) % 128;
  return mod < 31;
}

export function maxDaysInJalaliMonth(jalaliYear: number, jalaliMonth: number): number {
  if (jalaliMonth >= 1 && jalaliMonth <= 6) return 31;
  if (jalaliMonth >= 7 && jalaliMonth <= 11) return 30;
  return isLeapJalaliYear(jalaliYear) ? 30 : 29;
}

export function gregorianToJalali(gy: number, gm: number, gd: number): [number, number, number] {
  const gDaysInMonth = [0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334];
  const gy2 = gm > 2 ? gy + 1 : gy;
  let days =
    355666 +
    365 * gy +
    Math.floor((gy2 + 3) / 4) -
    Math.floor((gy2 + 99) / 100) +
    Math.floor((gy2 + 399) / 400) +
    gd +
    gDaysInMonth[gm - 1];
  let jy = -1595 + 33 * Math.floor(days / 12053);
  days %= 12053;
  jy += 4 * Math.floor(days / 1461);
  days %= 1461;
  if (days > 365) {
    jy += Math.floor((days - 1) / 365);
    days = (days - 1) % 365;
  }
  let jm: number;
  let jd: number;
  if (days < 186) {
    jm = 1 + Math.floor(days / 31);
    jd = 1 + (days % 31);
  } else {
    jm = 7 + Math.floor((days - 186) / 30);
    jd = 1 + ((days - 186) % 30);
  }
  return [jy, jm, jd];
}

export function jalaliToGregorian(jy: number, jm: number, jd: number): [number, number, number] {
  const jyAdj = jy + 1595;
  let days =
    -355668 +
    365 * jyAdj +
    Math.floor(jyAdj / 33) * 8 +
    Math.floor(((jyAdj % 33) + 3) / 4) +
    jd +
    (jm < 7 ? (jm - 1) * 31 : (jm - 7) * 30 + 186);
  let gy = 400 * Math.floor(days / 146097);
  days %= 146097;
  if (days > 36524) {
    days--;
    gy += 100 * Math.floor(days / 36524);
    days %= 36524;
    if (days >= 365) days++;
  }
  gy += 4 * Math.floor(days / 1461);
  days %= 1461;
  if (days > 365) {
    gy += Math.floor((days - 1) / 365);
    days = (days - 1) % 365;
  }
  let gd = days + 1;
  const isLeapG = (gy % 4 === 0 && gy % 100 !== 0) || gy % 400 === 0;
  const salA = [0, 31, isLeapG ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  let gm = 1;
  while (gm <= 12 && gd > salA[gm]) {
    gd -= salA[gm];
    gm++;
  }
  return [gy, gm, gd];
}

export function fromEpochMillisToJalali(epochMillis: number): JalaliDateTime {
  const date = new Date(epochMillis);
  const gy = date.getFullYear();
  const gm = date.getMonth() + 1;
  const gd = date.getDate();
  const [jy, jm, jd] = gregorianToJalali(gy, gm, gd);
  const dayOfWeek = date.getDay();

  return {
    year: jy,
    month: jm,
    day: jd,
    hour: date.getHours(),
    minute: date.getMinutes(),
    dayOfWeek,
    monthName: jalaliMonthName(jm),
    weekdayName: PERSIAN_WEEKDAYS[dayOfWeek] ?? '',
  };
}

export function jalaliToEpochMillis(
  jalaliYear: number,
  jalaliMonth: number,
  jalaliDay: number,
  hour: number = 9,
  minute: number = 0
): number {
  const safeMonth = Math.max(1, Math.min(12, jalaliMonth));
  const safeDay = Math.max(1, Math.min(maxDaysInJalaliMonth(jalaliYear, safeMonth), jalaliDay));
  const [gy, gm, gd] = jalaliToGregorian(jalaliYear, safeMonth, safeDay);
  const d = new Date(
    gy,
    gm - 1,
    gd,
    Math.max(0, Math.min(23, hour)),
    Math.max(0, Math.min(59, minute)),
    0,
    0
  );
  return d.getTime();
}

export function formatShamsiFullWithWeekday(epochMillis: number): string {
  const j = fromEpochMillisToJalali(epochMillis);
  const hh = String(j.hour).padStart(2, '0');
  const mm = String(j.minute).padStart(2, '0');
  return toPersianDigits(`${j.weekdayName} ${j.day} ${j.monthName} ${j.year} · ساعت ${hh}:${mm}`);
}

export function formatShamsiNumericDateTime(epochMillis: number): string {
  const j = fromEpochMillisToJalali(epochMillis);
  const m = String(j.month).padStart(2, '0');
  const d = String(j.day).padStart(2, '0');
  const hh = String(j.hour).padStart(2, '0');
  const mm = String(j.minute).padStart(2, '0');
  return toPersianDigits(`${j.year}/${m}/${d} ${hh}:${mm}`);
}

export function formatShamsiTimelineLabel(
  epochMillis: number,
  nowMillis: number = Date.now()
): string {
  const target = fromEpochMillisToJalali(epochMillis);
  const now = fromEpochMillisToJalali(nowMillis);
  const hh = String(target.hour).padStart(2, '0');
  const mm = String(target.minute).padStart(2, '0');
  const timePart = toPersianDigits(`${hh}:${mm}`);
  const shamsiDatePart = toPersianDigits(`${target.day} ${target.monthName} ${target.year}`);

  if (target.year === now.year && target.month === now.month && target.day === now.day) {
    return `امروز (${shamsiDatePart}) · ساعت ${timePart}`;
  }

  const tomorrow = fromEpochMillisToJalali(nowMillis + 24 * 60 * 60 * 1000);
  if (
    target.year === tomorrow.year &&
    target.month === tomorrow.month &&
    target.day === tomorrow.day
  ) {
    return `فردا (${shamsiDatePart}) · ساعت ${timePart}`;
  }

  return `${target.weekdayName} ${shamsiDatePart} · ساعت ${timePart}`;
}

export function formatRemainingDurationPersian(
  targetMillis: number,
  nowMillis: number = Date.now()
): string {
  const diffMillis = targetMillis - nowMillis;
  if (diffMillis <= 0) {
    const pastMinutes = Math.floor(Math.abs(diffMillis) / 60000);
    if (pastMinutes < 1) return 'هم‌اکنون';
    if (pastMinutes < 60) return toPersianDigits(`${pastMinutes} دقیقه پیش`);
    if (pastMinutes < 24 * 60) return toPersianDigits(`${Math.floor(pastMinutes / 60)} ساعت پیش`);
    return toPersianDigits(`${Math.floor(pastMinutes / (24 * 60))} روز پیش`);
  }

  const totalMinutes = Math.ceil(diffMillis / 60000);
  const days = Math.floor(totalMinutes / (24 * 60));
  const hours = Math.floor((totalMinutes % (24 * 60)) / 60);
  const minutes = totalMinutes % 60;

  let raw: string;
  if (days > 0 && hours > 0) {
    raw = `${days} روز و ${hours} ساعت دیگر`;
  } else if (days > 0) {
    raw = `${days} روز دیگر`;
  } else if (hours > 0 && minutes > 0) {
    raw = `${hours} ساعت و ${minutes} دقیقه دیگر`;
  } else if (hours > 0) {
    raw = `${hours} ساعت دیگر`;
  } else if (minutes > 1) {
    raw = `${minutes} دقیقه دیگر`;
  } else {
    raw = 'کمتر از ۱ دقیقه دیگر';
  }
  return toPersianDigits(raw);
}
