import React, { useState, useRef, useEffect } from 'react';
import {
  Mic,
  Send,
  Trash2,
  Volume2,
  RotateCcw,
  Square,
  Maximize2,
  Minimize2,
  Bell,
  BellOff,
  Calendar,
  Clock,
  Plus,
  Minus,
  X,
} from 'lucide-react';
import {
  CATEGORY_DEFINITIONS,
  ItemEntity,
  TextProcessor,
  formatPersianClockOnly,
  toPersianDigits,
} from '../nlp/textProcessor';
import {
  JALALI_MONTH_NAMES,
  formatRemainingDurationPersian,
  formatShamsiFullWithWeekday,
  formatShamsiTimelineLabel,
  fromEpochMillisToJalali,
  jalaliMonthName,
  jalaliToEpochMillis,
  maxDaysInJalaliMonth,
} from '../nlp/jalaliCalendar';

interface AndroidSimulatorProps {
  items: ItemEntity[];
  onAddItem: (rawText: string, explicitShamsiMillis?: number | null) => void;
  onUpdateItemAlarm?: (id: number, newEpochMillis: number | null) => void;
  onDeleteItem: (id: number) => void;
  onResetDefaults: () => void;
  fullAppMode?: boolean;
  onToggleFullAppMode?: () => void;
}

const PERSIAN_VOICE_SAMPLES = [
  'یادم باشه فردا ساعت ۸ صبح بیدار شم',
  'یادآوری جلسه کاری ۱۰ آبان ساعت ۱۰ صبح',
  'تبریک تولد سارا ۱۵ آبان ساعت ۶ عصر',
  'یادم باشه ۲ ساعت دیگه داروهامو بخورم',
  'ایده برای طراحی اپلیکیشن تقویم و یادآور شمسی',
];

export const AndroidSimulator: React.FC<AndroidSimulatorProps> = ({
  items,
  onAddItem,
  onUpdateItemAlarm,
  onDeleteItem,
  onResetDefaults,
  fullAppMode = false,
  onToggleFullAppMode,
}) => {
  const [nowMillis, setNowMillis] = useState<number>(() => Date.now());
  const [showActiveOnly, setShowActiveOnly] = useState<boolean>(false);
  const [isExpanded, setIsExpanded] = useState(false);
  const [draftText, setDraftText] = useState('');
  const [customDraftShamsiMillis, setCustomDraftShamsiMillis] = useState<number | null>(null);
  const [isListening, setIsListening] = useState(false);
  const [partialSpeech, setPartialSpeech] = useState('');
  const [sampleIndex, setSampleIndex] = useState(0);
  const [voiceModeLabel, setVoiceModeLabel] = useState<string>('SpeechRecognizer (fa-IR)');
  const [snackbarMessage, setSnackbarMessage] = useState<string | null>(null);

  // Shamsi Alarm Picker Modal state
  const [pickerTargetItem, setPickerTargetItem] = useState<ItemEntity | null>(null);
  const [isDraftPickerOpen, setIsDraftPickerOpen] = useState<boolean>(false);
  const [selYear, setSelYear] = useState<number>(1405);
  const [selMonth, setSelMonth] = useState<number>(8);
  const [selDay, setSelDay] = useState<number>(7);
  const [selHour, setSelHour] = useState<number>(9);
  const [selMinute, setSelMinute] = useState<number>(0);

  const inputRef = useRef<HTMLInputElement | null>(null);
  const voiceTimerRef = useRef<number | null>(null);
  const snackbarTimerRef = useRef<number | null>(null);
  const recognitionRef = useRef<any>(null);

  // Live "زمان اکنون" clock ticker
  useEffect(() => {
    const id = window.setInterval(() => {
      setNowMillis(Date.now());
    }, 10000);
    return () => window.clearInterval(id);
  }, []);

  const showSnackbar = (msg: string) => {
    setSnackbarMessage(msg);
    if (snackbarTimerRef.current) {
      window.clearTimeout(snackbarTimerRef.current);
    }
    snackbarTimerRef.current = window.setTimeout(() => {
      setSnackbarMessage(null);
    }, 4200);
  };

  const openShamsiPickerWithTime = (baseEpochMillis: number, targetItem: ItemEntity | null) => {
    const jalali = fromEpochMillisToJalali(baseEpochMillis);
    setSelYear(jalali.year);
    setSelMonth(jalali.month);
    setSelDay(jalali.day);
    setSelHour(jalali.hour);
    setSelMinute(jalali.minute);
    if (targetItem) {
      setPickerTargetItem(targetItem);
      setIsDraftPickerOpen(false);
    } else {
      setPickerTargetItem(null);
      setIsDraftPickerOpen(true);
    }
  };

  const commitItemWithAlarmCheck = (rawText: string, explicitMillis?: number | null) => {
    const clean = rawText.trim();
    if (!clean) return;
    const processed = TextProcessor.process(clean, nowMillis);
    const alarmTime =
      explicitMillis !== undefined && explicitMillis !== null
        ? explicitMillis
        : customDraftShamsiMillis ?? processed.extractedTime;

    onAddItem(clean, alarmTime);

    if (alarmTime) {
      const shamsiStr = formatShamsiFullWithWeekday(alarmTime);
      showSnackbar(`آلارم شمسی تنظیم شد: ${shamsiStr}`);
    }
    setCustomDraftShamsiMillis(null);
  };

  useEffect(() => {
    if (isExpanded && !isListening) {
      inputRef.current?.focus();
    }
  }, [isExpanded, isListening]);

  useEffect(() => {
    return () => {
      if (voiceTimerRef.current) {
        window.clearInterval(voiceTimerRef.current);
      }
      if (snackbarTimerRef.current) {
        window.clearTimeout(snackbarTimerRef.current);
      }
      if (recognitionRef.current) {
        try {
          recognitionRef.current.stop();
        } catch {
          // ignore
        }
      }
    };
  }, []);

  const stopVoiceCapture = () => {
    if (voiceTimerRef.current) {
      window.clearInterval(voiceTimerRef.current);
      voiceTimerRef.current = null;
    }
    if (recognitionRef.current) {
      try {
        recognitionRef.current.onend = null;
        recognitionRef.current.stop();
      } catch {
        // ignore
      }
      recognitionRef.current = null;
    }
    setIsListening(false);
  };

  const startSimulatedSpeechRecognizerCapture = () => {
    if (typeof navigator !== 'undefined' && navigator.onLine === false) {
      showSnackbar(
        'قطعی ارتباط با اینترنت: لطفاً اتصال اینترنت خود را بررسی کرده و دوباره تلاش کنید.'
      );
      return;
    }
    setVoiceModeLabel('SpeechRecognizer (fa-IR)');
    const targetPhrase = PERSIAN_VOICE_SAMPLES[sampleIndex % PERSIAN_VOICE_SAMPLES.length];
    setSampleIndex((prev) => prev + 1);
    setIsExpanded(true);
    setIsListening(true);
    setPartialSpeech('');

    const words = targetPhrase.split(' ');
    let step = 0;

    voiceTimerRef.current = window.setInterval(() => {
      step += 1;
      if (step <= words.length) {
        setPartialSpeech(words.slice(0, step).join(' '));
      } else {
        stopVoiceCapture();
        commitItemWithAlarmCheck(targetPhrase);
        setPartialSpeech('');
        setDraftText('');
        setIsExpanded(false);
      }
    }, 250);
  };

  const handleToggleVoice = () => {
    if (isListening) {
      const currentTranscript = partialSpeech.trim();
      stopVoiceCapture();
      if (currentTranscript) {
        commitItemWithAlarmCheck(currentTranscript);
        setPartialSpeech('');
        setDraftText('');
        setIsExpanded(false);
      }
      return;
    }

    if (typeof navigator !== 'undefined' && navigator.onLine === false) {
      showSnackbar(
        'خطا در اتصال به اینترنت: لطفاً اتصال اینترنت خود را بررسی کرده و دوباره تلاش کنید.'
      );
      return;
    }

    const SpeechRecognitionApi =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;

    if (SpeechRecognitionApi) {
      try {
        const recognition = new SpeechRecognitionApi();
        recognition.lang = 'fa-IR';
        recognition.continuous = false;
        recognition.interimResults = true;

        let latestTranscript = '';

        recognition.onstart = () => {
          setVoiceModeLabel('SpeechRecognizer آنلاین (fa-IR)');
          setIsExpanded(true);
          setIsListening(true);
          setPartialSpeech('');
        };

        recognition.onresult = (event: any) => {
          let transcript = '';
          for (let i = 0; i < event.results.length; i++) {
            transcript += event.results[i][0].transcript;
          }
          latestTranscript = transcript.trim();
          setPartialSpeech(latestTranscript);
        };

        recognition.onerror = () => {
          stopVoiceCapture();
          startSimulatedSpeechRecognizerCapture();
        };

        recognition.onend = () => {
          setIsListening(false);
          if (latestTranscript) {
            commitItemWithAlarmCheck(latestTranscript);
            setPartialSpeech('');
            setDraftText('');
            setIsExpanded(false);
          }
        };

        recognitionRef.current = recognition;
        recognition.start();
        return;
      } catch {
        // Fallback to simulated Persian stream
      }
    }

    startSimulatedSpeechRecognizerCapture();
  };

  const handleSubmit = (e?: React.FormEvent) => {
    e?.preventDefault();
    const activeText = (isListening ? partialSpeech : draftText).trim();
    if (!activeText) return;

    stopVoiceCapture();
    commitItemWithAlarmCheck(activeText);
    setDraftText('');
    setPartialSpeech('');
    setIsExpanded(false);
  };

  const activeText = isListening ? partialSpeech : draftText;
  const livePreview = activeText.trim() ? TextProcessor.process(activeText, nowMillis) : null;

  // Separate active reminders vs past/general items relative to "زمان اکنون"
  const activeReminders = items
    .filter((it) => it.extractedTime !== null && it.extractedTime > nowMillis)
    .sort((a, b) => (a.extractedTime ?? 0) - (b.extractedTime ?? 0));

  const pastOrGeneralItems = items
    .filter((it) => it.extractedTime === null || it.extractedTime <= nowMillis)
    .sort((a, b) => (b.extractedTime ?? b.createdAt) - (a.extractedTime ?? a.createdAt));

  const nowJalali = fromEpochMillisToJalali(nowMillis);
  const nowClockStr = toPersianDigits(
    `${String(nowJalali.hour).padStart(2, '0')}:${String(nowJalali.minute).padStart(2, '0')}`
  );
  const nowFullShamsiDate = toPersianDigits(
    `${nowJalali.weekdayName} ${nowJalali.day} ${nowJalali.monthName} ${nowJalali.year}`
  );

  // Compute picker preview epoch millis
  const maxPickerDays = maxDaysInJalaliMonth(selYear, selMonth);
  const clampedDay = Math.min(selDay, maxPickerDays);
  const pickerPreviewMillis = jalaliToEpochMillis(
    selYear,
    selMonth,
    clampedDay,
    selHour,
    selMinute
  );

  const handleConfirmShamsiPicker = () => {
    if (pickerTargetItem) {
      onUpdateItemAlarm?.(pickerTargetItem.id, pickerPreviewMillis);
      showSnackbar(
        `آلارم به تاریخ شمسی ${formatShamsiFullWithWeekday(pickerPreviewMillis)} بروزرسانی شد`
      );
      setPickerTargetItem(null);
    } else if (isDraftPickerOpen) {
      setCustomDraftShamsiMillis(pickerPreviewMillis);
      setIsDraftPickerOpen(false);
      setIsExpanded(true);
    }
  };

  return (
    <div className="flex flex-col">
      {/* Context Bar Above Device */}
      <div className="flex items-center justify-between mb-3 px-1">
        <div dir="rtl" className="font-persian">
          <h2 className="text-sm font-semibold text-stone-900">
            اپلیکیشن یاد (Yaad) · تقویم شمسی و خط زمان اکنون
          </h2>
          <p className="text-xs text-stone-500">
            یادآورهای فعال بالای خط زمان «اکنون» قرار می‌گیرند و قابلیت تنظیم آلارم شمسی دارند
          </p>
        </div>
        <div className="flex items-center gap-1.5 shrink-0">
          {onToggleFullAppMode && (
            <button
              type="button"
              onClick={onToggleFullAppMode}
              className="inline-flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-medium text-stone-700 hover:text-stone-900 bg-white border border-stone-200 hover:bg-stone-100 rounded-lg transition-colors whitespace-nowrap shrink-0 cursor-pointer"
              title={fullAppMode ? 'بازگشت به نمای ترکیبی' : 'نمای تمام‌صفحه اپلیکیشن'}
            >
              {fullAppMode ? (
                <>
                  <Minimize2 className="w-3.5 h-3.5" />
                  <span>Split View</span>
                </>
              ) : (
                <>
                  <Maximize2 className="w-3.5 h-3.5" />
                  <span>Full App</span>
                </>
              )}
            </button>
          )}
          <button
            type="button"
            onClick={onResetDefaults}
            className="inline-flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-medium text-stone-600 hover:text-stone-900 bg-stone-100 hover:bg-stone-200/70 rounded-lg transition-colors whitespace-nowrap shrink-0 cursor-pointer"
            title="بازنشانی نمونه آیتم‌ها"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Reset</span>
          </button>
        </div>
      </div>

      {/* Minimalist Android Viewport Container */}
      <div
        className={`relative mx-auto w-full bg-white rounded-[32px] border border-stone-200 shadow-sm flex flex-col overflow-hidden transition-all ${
          fullAppMode ? 'max-w-[560px] h-[740px]' : 'max-w-[420px] h-[680px]'
        }`}
      >
        {/* Minimal Android Status Bar */}
        <div className="flex items-center justify-between px-6 pt-3 pb-2 text-[11px] text-stone-400 font-mono-tabular select-none border-b border-stone-100">
          <span>{nowClockStr}</span>
          <span className="font-persian text-stone-500 font-medium">
            یاد · {isListening ? voiceModeLabel : 'تقویم شمسی + SpeechRecognizer'}
          </span>
          <span>100%</span>
        </div>

        {/* Live "اکنون (Now)" Top Shamsi Bar + Active Reminders Filter */}
        <div
          dir="rtl"
          className="px-5 py-2.5 bg-stone-50/90 border-b border-stone-100 flex items-center justify-between gap-2 font-persian"
        >
          <div className="flex flex-col">
            <div className="flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
              <span className="text-xs font-semibold text-stone-900">
                اکنون · {nowFullShamsiDate}
              </span>
            </div>
            <span className="text-[11px] text-stone-500 mt-0.5">
              ساعت فعلی: {nowClockStr} · تقویم هجری شمسی
            </span>
          </div>

          <button
            type="button"
            onClick={() => setShowActiveOnly((prev) => !prev)}
            className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[11px] font-medium transition-colors cursor-pointer shrink-0 ${
              showActiveOnly
                ? 'bg-blue-700 text-white'
                : 'bg-blue-50 text-blue-700 hover:bg-blue-100'
            }`}
          >
            <Bell className="w-3.5 h-3.5" />
            <span>
              {showActiveOnly
                ? 'نمایش کل تایم‌لاین'
                : `${toPersianDigits(activeReminders.length)} یادآور فعال`}
            </span>
          </button>
        </div>

        {/* Main Screen: Timeline Anchored Around "زمان اکنون" */}
        <div
          dir="rtl"
          className="flex-1 overflow-y-auto px-5 pt-4 pb-32 font-persian select-text space-y-3"
        >
          {items.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center text-center">
              <p className="text-sm text-stone-400">بنویسید یا صحبت کنید...</p>
            </div>
          ) : (
            <>
              {/* 1. Active Upcoming Reminders Section (Above "زمان اکنون") */}
              {activeReminders.length > 0 && (
                <div className="space-y-2.5">
                  <div className="flex items-center justify-between px-1">
                    <div className="flex items-center gap-1.5 text-xs font-semibold text-blue-700">
                      <Bell className="w-3.5 h-3.5" />
                      <span>
                        یادآورهای فعال ({toPersianDigits(activeReminders.length)})
                      </span>
                    </div>
                    <span className="text-[11px] text-stone-400">
                      مرتب‌شده بر اساس نزدیک‌ترین آلارم شمسی
                    </span>
                  </div>

                  {activeReminders.map((item) => {
                    const catMeta = CATEGORY_DEFINITIONS[item.category];
                    const remainingText = item.extractedTime
                      ? formatRemainingDurationPersian(item.extractedTime, nowMillis)
                      : '';
                    const shamsiLabel = item.extractedTime
                      ? formatShamsiTimelineLabel(item.extractedTime, nowMillis)
                      : '';

                    return (
                      <div
                        key={item.id}
                        className="group p-3.5 rounded-2xl bg-blue-50/40 border border-blue-200/80 transition-all space-y-2"
                      >
                        {/* Active Status Strip */}
                        <div className="flex items-center justify-between gap-2">
                          <div className="flex items-center gap-1.5 text-[11px]">
                            <span className="w-2 h-2 rounded-full bg-blue-600 animate-pulse" />
                            <span className="font-bold text-blue-800">یادآور فعال</span>
                            <span className="text-blue-300">·</span>
                            <span className="font-medium text-emerald-700">
                              زنگ در {remainingText}
                            </span>
                          </div>

                          <div className="flex items-center gap-1">
                            <button
                              type="button"
                              onClick={() =>
                                openShamsiPickerWithTime(
                                  item.extractedTime ?? nowMillis + 3600000,
                                  item
                                )
                              }
                              className="px-2 py-0.5 text-[11px] font-medium text-blue-700 hover:bg-blue-100/80 rounded-md transition-colors cursor-pointer"
                            >
                              تغییر زمان شمسی
                            </button>
                            <button
                              type="button"
                              onClick={() => {
                                onUpdateItemAlarm?.(item.id, null);
                                showSnackbar('آلارم یادآور غیرفعال شد');
                              }}
                              className="p-1 text-stone-400 hover:text-amber-600 rounded-md transition-colors cursor-pointer"
                              title="لغو آلارم"
                            >
                              <BellOff className="w-3.5 h-3.5" />
                            </button>
                            <button
                              type="button"
                              onClick={() => onDeleteItem(item.id)}
                              className="p-1 text-stone-400 hover:text-red-600 rounded-md transition-colors cursor-pointer"
                              title="حذف"
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        </div>

                        {/* Main Reminder Text */}
                        <p className="text-[14.5px] font-medium leading-relaxed text-stone-900 break-words">
                          {item.originalText}
                        </p>

                        {/* Shamsi Date & Metadata */}
                        <div className="flex flex-wrap items-center gap-1.5 text-[11.5px] text-blue-900/80">
                          <span className="font-semibold">{catMeta.persianLabel}</span>
                          <span>·</span>
                          <span className="inline-flex items-center gap-1 text-blue-800 font-medium">
                            <Calendar className="w-3 h-3" />
                            <span>آلارم شمسی: {shamsiLabel}</span>
                          </span>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}

              {/* 2. Live "خط زمان اکنون" (Now Timeline Divider) */}
              <div className="py-2 flex items-center gap-2.5 select-none">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 ring-4 ring-emerald-500/15 shrink-0" />
                <span className="text-xs font-semibold text-emerald-800 whitespace-nowrap">
                  خط زمان اکنون · {toPersianDigits(`${nowJalali.day} ${nowJalali.monthName} ${nowJalali.year}`)} · ساعت {nowClockStr}
                </span>
                <div className="h-px bg-emerald-200 flex-1" />
              </div>

              {/* 3. Past / General Timeline Items (Below "زمان اکنون") */}
              {!showActiveOnly && pastOrGeneralItems.length > 0 && (
                <div className="space-y-1">
                  <div className="text-[11px] font-medium text-stone-400 px-1 pb-1">
                    تایم‌لاین یادداشت‌ها و یادآورهای گذشته
                  </div>

                  <div className="divide-y divide-stone-100">
                    {pastOrGeneralItems.map((item) => {
                      const catMeta = CATEGORY_DEFINITIONS[item.category];
                      const shamsiLabel = item.extractedTime
                        ? formatShamsiTimelineLabel(item.extractedTime, nowMillis)
                        : null;

                      return (
                        <div
                          key={item.id}
                          className="group py-3.5 transition-colors flex items-start justify-between gap-3"
                        >
                          <div className="flex-1 min-w-0">
                            <p className="text-[14.5px] leading-relaxed text-stone-800 break-words">
                              {item.originalText}
                            </p>

                            <div className="mt-1.5 flex flex-wrap items-center justify-between gap-2 text-xs text-stone-500">
                              <div className="flex flex-wrap items-center gap-1.5">
                                <span
                                  className={
                                    item.category === 'REMINDER'
                                      ? 'text-stone-600 font-medium'
                                      : item.category === 'OCCASION'
                                      ? 'text-amber-700 font-medium'
                                      : item.category === 'IDEA'
                                      ? 'text-emerald-700 font-medium'
                                      : 'text-stone-500'
                                  }
                                >
                                  {catMeta.persianLabel}
                                </span>

                                {shamsiLabel && (
                                  <>
                                    <span aria-hidden="true">·</span>
                                    <span className="text-stone-400 line-through">
                                      گذشته: {shamsiLabel}
                                    </span>
                                  </>
                                )}

                                <span aria-hidden="true">·</span>
                                <span className="text-stone-400">
                                  ثبت: {formatPersianClockOnly(item.createdAt)}
                                </span>
                              </div>

                              <button
                                type="button"
                                onClick={() =>
                                  openShamsiPickerWithTime(
                                    nowMillis + 60 * 60 * 1000,
                                    item
                                  )
                                }
                                className="inline-flex items-center gap-1 px-2 py-0.5 text-[11px] font-medium text-stone-600 hover:text-blue-700 hover:bg-stone-100 rounded-md transition-colors cursor-pointer"
                              >
                                <Calendar className="w-3 h-3" />
                                <span>تنظیم آلارم شمسی</span>
                              </button>
                            </div>
                          </div>

                          <button
                            type="button"
                            onClick={() => onDeleteItem(item.id)}
                            className="opacity-0 group-hover:opacity-100 focus:opacity-100 p-1.5 text-stone-400 hover:text-red-600 transition-opacity rounded-lg cursor-pointer"
                            aria-label="حذف آیتم"
                            title="حذف"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}
            </>
          )}
        </div>

        {/* Live Rule-Based Shamsi NLP Preview Strip (when typing or speaking) */}
        {livePreview && (
          <div
            dir="rtl"
            className="mx-5 mb-2 px-3.5 py-2 bg-stone-50 border border-stone-200/80 rounded-xl font-persian flex items-center justify-between text-xs text-stone-600"
          >
            <div className="flex items-center gap-2 truncate">
              <span className="text-stone-400">تشخیص خودکار:</span>
              <span className="font-semibold text-stone-800">
                {CATEGORY_DEFINITIONS[livePreview.category].persianLabel}
              </span>
              {(customDraftShamsiMillis || livePreview.extractedTime) && (
                <>
                  <span>·</span>
                  <span className="text-blue-700 font-medium">
                    آلارم شمسی:{' '}
                    {formatShamsiTimelineLabel(
                      customDraftShamsiMillis ?? livePreview.extractedTime!,
                      nowMillis
                    )}
                  </span>
                </>
              )}
            </div>
            {isListening && (
              <span className="inline-flex items-center gap-1 text-red-600 font-medium shrink-0">
                <Volume2 className="w-3.5 h-3.5 animate-pulse" />
                <span>در حال شنیدن...</span>
              </span>
            )}
          </div>
        )}

        {/* Compose SnackbarHost Equivalent (Alarm Scheduled Confirmation) */}
        {snackbarMessage && (
          <div
            dir="rtl"
            className="mx-5 mb-2 px-4 py-2.5 bg-stone-900 text-white rounded-xl shadow-md font-persian flex items-center justify-between text-xs z-20"
          >
            <span>{snackbarMessage}</span>
            <span className="text-emerald-400 font-medium mr-2 shrink-0">AlarmManager</span>
          </div>
        )}

        {/* Custom Shamsi Draft Chip if selected before typing */}
        {customDraftShamsiMillis && (
          <div
            dir="rtl"
            className="mx-5 mb-1.5 px-3 py-1.5 bg-blue-50 border border-blue-200 rounded-xl font-persian flex items-center justify-between text-xs text-blue-800"
          >
            <button
              type="button"
              onClick={() => openShamsiPickerWithTime(customDraftShamsiMillis, null)}
              className="flex items-center gap-1.5 font-medium cursor-pointer"
            >
              <Calendar className="w-3.5 h-3.5 text-blue-700" />
              <span>آلارم شمسی انتخابی: {formatShamsiFullWithWeekday(customDraftShamsiMillis)}</span>
            </button>
            <button
              type="button"
              onClick={() => setCustomDraftShamsiMillis(null)}
              className="text-red-600 hover:text-red-700 text-[11px] font-medium cursor-pointer"
            >
              حذف
            </button>
          </div>
        )}

        {/* Bottom Area: Single Expandable FAB */}
        <div
          dir="rtl"
          className="px-5 pb-4 pt-1 bg-gradient-to-t from-white via-white to-white/80 font-persian"
        >
          {!isExpanded ? (
            <div className="flex justify-center">
              <div className="inline-flex items-center bg-stone-100 hover:bg-stone-200/70 transition-colors rounded-full p-1.5 border border-stone-200/60">
                <button
                  type="button"
                  onClick={handleToggleVoice}
                  className="w-11 h-11 rounded-full bg-stone-900 text-white flex items-center justify-center hover:bg-stone-800 transition-colors cursor-pointer"
                  aria-label="شروع ضبط صوتی فارسی"
                  title="ثبت صوتی فارسی"
                >
                  <Mic className="w-5 h-5" />
                </button>
                <button
                  type="button"
                  onClick={() => setIsExpanded(true)}
                  className="px-3.5 py-2 text-sm text-stone-600 hover:text-stone-900 cursor-pointer"
                >
                  بنویسید یا صحبت کنید...
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setIsExpanded(true);
                    openShamsiPickerWithTime(nowMillis + 60 * 60 * 1000, null);
                  }}
                  className="w-9 h-9 rounded-full text-blue-700 hover:bg-blue-100/70 flex items-center justify-center transition-colors cursor-pointer"
                  title="تنظیم آلارم با تقویم شمسی"
                >
                  <Calendar className="w-4 h-4" />
                </button>
              </div>
            </div>
          ) : (
            <form
              onSubmit={handleSubmit}
              className="flex items-center gap-1.5 bg-stone-100 rounded-full px-3 py-1.5 border border-stone-200"
            >
              <button
                type="button"
                onClick={handleToggleVoice}
                className={`w-9 h-9 rounded-full flex items-center justify-center transition-colors shrink-0 cursor-pointer ${
                  isListening
                    ? 'bg-red-600 text-white'
                    : 'bg-stone-200 text-stone-800 hover:bg-stone-300'
                }`}
                aria-label={isListening ? 'توقف ضبط' : 'ورودی صوتی'}
                title={isListening ? 'توقف ضبط و ثبت' : 'شروع تشخیص گفتار فارسی'}
              >
                {isListening ? <Square className="w-3.5 h-3.5" /> : <Mic className="w-4 h-4" />}
              </button>

              <button
                type="button"
                onClick={() =>
                  openShamsiPickerWithTime(
                    customDraftShamsiMillis ?? nowMillis + 60 * 60 * 1000,
                    null
                  )
                }
                className={`w-9 h-9 rounded-full flex items-center justify-center transition-colors shrink-0 cursor-pointer ${
                  customDraftShamsiMillis
                    ? 'bg-blue-600 text-white'
                    : 'bg-stone-200/80 text-stone-700 hover:bg-stone-300'
                }`}
                title="تنظیم تاریخ و ساعت شمسی"
              >
                <Calendar className="w-4 h-4" />
              </button>

              <input
                ref={inputRef}
                type="text"
                value={activeText}
                onChange={(e) => {
                  if (isListening) stopVoiceCapture();
                  setDraftText(e.target.value);
                }}
                placeholder={
                  isListening
                    ? 'در حال شنیدن صدای شما...'
                    : 'مثلاً: یادآوری جلسه ۱۰ آبان ساعت ۹ صبح...'
                }
                className="flex-1 bg-transparent text-sm text-stone-900 placeholder:text-stone-400 focus:outline-none px-1.5"
              />

              <button
                type="submit"
                disabled={!activeText.trim()}
                className={`w-9 h-9 rounded-full flex items-center justify-center transition-colors shrink-0 ${
                  activeText.trim()
                    ? 'bg-stone-900 text-white hover:bg-stone-800 cursor-pointer'
                    : 'bg-transparent text-stone-300 cursor-not-allowed'
                }`}
                aria-label="ثبت یادداشت"
              >
                <Send className="w-4 h-4" />
              </button>
            </form>
          )}
        </div>

        {/* Interactive Shamsi Calendar Alarm Picker Modal */}
        {(pickerTargetItem !== null || isDraftPickerOpen) && (
          <div
            dir="rtl"
            className="absolute inset-0 bg-stone-950/45 backdrop-blur-[1px] z-30 flex items-center justify-center p-4 font-persian"
          >
            <div className="w-full max-w-[360px] bg-white rounded-2xl border border-stone-200 shadow-xl p-4 space-y-3.5">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <h3 className="text-sm font-bold text-stone-900">
                    تنظیم آلارم به تقویم شمسی
                  </h3>
                  <p className="text-xs text-stone-500 line-clamp-1 mt-0.5">
                    {pickerTargetItem
                      ? pickerTargetItem.originalText
                      : draftText.trim() || 'یادآور جدید'}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    setPickerTargetItem(null);
                    setIsDraftPickerOpen(false);
                  }}
                  className="p-1 text-stone-400 hover:text-stone-700 rounded-lg cursor-pointer"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              {/* Selected Shamsi Date Live Preview Banner */}
              <div className="p-2.5 rounded-xl bg-blue-50 border border-blue-200/80 text-center">
                <div className="text-xs font-bold text-blue-800">
                  {formatShamsiFullWithWeekday(pickerPreviewMillis)}
                </div>
                <div className="text-[11px] text-emerald-700 mt-0.5">
                  {pickerPreviewMillis > nowMillis
                    ? `زنگ در ${formatRemainingDurationPersian(pickerPreviewMillis, nowMillis)}`
                    : 'زمان انتخاب‌شده در گذشته است'}
                </div>
              </div>

              {/* Quick Shamsi Presets */}
              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  onClick={() =>
                    openShamsiPickerWithTime(
                      nowMillis + 60 * 60 * 1000,
                      pickerTargetItem
                    )
                  }
                  className="flex-1 py-1.5 px-2 text-[11px] font-medium bg-stone-100 hover:bg-stone-200 text-stone-700 rounded-lg cursor-pointer"
                >
                  ۱ ساعت دیگر
                </button>
                <button
                  type="button"
                  onClick={() => {
                    const tomorrow = fromEpochMillisToJalali(nowMillis + 24 * 3600 * 1000);
                    setSelYear(tomorrow.year);
                    setSelMonth(tomorrow.month);
                    setSelDay(tomorrow.day);
                    setSelHour(8);
                    setSelMinute(0);
                  }}
                  className="flex-1 py-1.5 px-2 text-[11px] font-medium bg-stone-100 hover:bg-stone-200 text-stone-700 rounded-lg cursor-pointer"
                >
                  فردا ۸ صبح
                </button>
                <button
                  type="button"
                  onClick={() => {
                    const pasFarda = fromEpochMillisToJalali(nowMillis + 48 * 3600 * 1000);
                    setSelYear(pasFarda.year);
                    setSelMonth(pasFarda.month);
                    setSelDay(pasFarda.day);
                    setSelHour(10);
                    setSelMinute(0);
                  }}
                  className="flex-1 py-1.5 px-2 text-[11px] font-medium bg-stone-100 hover:bg-stone-200 text-stone-700 rounded-lg cursor-pointer"
                >
                  پس‌فردا ۱۰ صبح
                </button>
              </div>

              {/* Shamsi Day / Month / Year Selectors */}
              <div className="space-y-1.5">
                <label className="text-[11px] font-medium text-stone-600 block">
                  تاریخ هجری شمسی (روز / ماه / سال):
                </label>
                <div className="grid grid-cols-3 gap-2">
                  {/* Day */}
                  <div className="bg-stone-100 rounded-xl p-2 flex flex-col items-center">
                    <span className="text-[10px] text-stone-500">روز</span>
                    <div className="flex items-center justify-between w-full mt-1">
                      <button
                        type="button"
                        onClick={() =>
                          setSelDay((d) => (d < maxPickerDays ? d + 1 : 1))
                        }
                        className="p-1 hover:bg-stone-200 rounded cursor-pointer"
                      >
                        <Plus className="w-3 h-3" />
                      </button>
                      <span className="text-xs font-bold text-stone-900">
                        {toPersianDigits(clampedDay)}
                      </span>
                      <button
                        type="button"
                        onClick={() =>
                          setSelDay((d) => (d > 1 ? d - 1 : maxPickerDays))
                        }
                        className="p-1 hover:bg-stone-200 rounded cursor-pointer"
                      >
                        <Minus className="w-3 h-3" />
                      </button>
                    </div>
                  </div>

                  {/* Month */}
                  <div className="bg-stone-100 rounded-xl p-2 flex flex-col items-center">
                    <span className="text-[10px] text-stone-500">ماه شمسی</span>
                    <select
                      value={selMonth}
                      onChange={(e) => setSelMonth(Number(e.target.value))}
                      className="mt-1 bg-transparent text-xs font-bold text-stone-900 focus:outline-none text-center cursor-pointer"
                    >
                      {JALALI_MONTH_NAMES.map((name, idx) => (
                        <option key={name} value={idx + 1}>
                          {name}
                        </option>
                      ))}
                    </select>
                  </div>

                  {/* Year */}
                  <div className="bg-stone-100 rounded-xl p-2 flex flex-col items-center">
                    <span className="text-[10px] text-stone-500">سال</span>
                    <div className="flex items-center justify-between w-full mt-1">
                      <button
                        type="button"
                        onClick={() => setSelYear((y) => y + 1)}
                        className="p-1 hover:bg-stone-200 rounded cursor-pointer"
                      >
                        <Plus className="w-3 h-3" />
                      </button>
                      <span className="text-xs font-bold text-stone-900">
                        {toPersianDigits(selYear)}
                      </span>
                      <button
                        type="button"
                        onClick={() => setSelYear((y) => y - 1)}
                        className="p-1 hover:bg-stone-200 rounded cursor-pointer"
                      >
                        <Minus className="w-3 h-3" />
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              {/* Hour & Minute Selectors */}
              <div className="space-y-1.5">
                <label className="text-[11px] font-medium text-stone-600 block">
                  ساعت آلارم (۲۴ ساعته):
                </label>
                <div className="grid grid-cols-2 gap-2">
                  {/* Minute */}
                  <div className="bg-stone-100 rounded-xl p-2 flex flex-col items-center">
                    <span className="text-[10px] text-stone-500">دقیقه</span>
                    <div className="flex items-center justify-between w-full mt-1 px-2">
                      <button
                        type="button"
                        onClick={() => setSelMinute((m) => (m + 5) % 60)}
                        className="p-1 hover:bg-stone-200 rounded cursor-pointer"
                      >
                        <Plus className="w-3 h-3" />
                      </button>
                      <span className="text-xs font-bold text-stone-900">
                        {toPersianDigits(String(selMinute).padStart(2, '0'))}
                      </span>
                      <button
                        type="button"
                        onClick={() => setSelMinute((m) => (m + 55) % 60)}
                        className="p-1 hover:bg-stone-200 rounded cursor-pointer"
                      >
                        <Minus className="w-3 h-3" />
                      </button>
                    </div>
                  </div>

                  {/* Hour */}
                  <div className="bg-stone-100 rounded-xl p-2 flex flex-col items-center">
                    <span className="text-[10px] text-stone-500">ساعت</span>
                    <div className="flex items-center justify-between w-full mt-1 px-2">
                      <button
                        type="button"
                        onClick={() => setSelHour((h) => (h + 1) % 24)}
                        className="p-1 hover:bg-stone-200 rounded cursor-pointer"
                      >
                        <Plus className="w-3 h-3" />
                      </button>
                      <span className="text-xs font-bold text-stone-900">
                        {toPersianDigits(String(selHour).padStart(2, '0'))}
                      </span>
                      <button
                        type="button"
                        onClick={() => setSelHour((h) => (h + 23) % 24)}
                        className="p-1 hover:bg-stone-200 rounded cursor-pointer"
                      >
                        <Minus className="w-3 h-3" />
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="flex items-center justify-end gap-2 pt-1">
                <button
                  type="button"
                  onClick={() => {
                    setPickerTargetItem(null);
                    setIsDraftPickerOpen(false);
                  }}
                  className="px-3 py-1.5 text-xs font-medium text-stone-600 hover:bg-stone-100 rounded-lg cursor-pointer"
                >
                  انصراف
                </button>
                <button
                  type="button"
                  onClick={handleConfirmShamsiPicker}
                  className="px-4 py-1.5 text-xs font-medium text-white bg-stone-900 hover:bg-stone-800 rounded-lg cursor-pointer"
                >
                  ثبت آلارم شمسی
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
