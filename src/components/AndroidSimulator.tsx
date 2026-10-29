import React, { useState, useRef, useEffect } from 'react';
import { Mic, Send, Trash2, Volume2, RotateCcw, Square, Maximize2, Minimize2 } from 'lucide-react';
import {
  CATEGORY_DEFINITIONS,
  ItemEntity,
  TextProcessor,
  formatPersianClockOnly,
  formatPersianTimestamp,
  toPersianDigits,
} from '../nlp/textProcessor';

interface AndroidSimulatorProps {
  items: ItemEntity[];
  onAddItem: (rawText: string) => void;
  onDeleteItem: (id: number) => void;
  onResetDefaults: () => void;
  fullAppMode?: boolean;
  onToggleFullAppMode?: () => void;
}

const VOSK_PERSIAN_VOICE_SAMPLES = [
  'یادم باشه فردا ساعت ۸ صبح بیدار شم',
  'تبریک تولد سارا فردا ساعت ۶ عصر',
  'ایده برای طراحی اپلیکیشن یادداشت صوتی آفلاین',
  'یادآوری تماس با دکتر پس‌فردا ساعت ۱۰ صبح',
  'پیشنهاد طرح جدید برای صفحه تایم‌لاین مینیمال',
];

export const AndroidSimulator: React.FC<AndroidSimulatorProps> = ({
  items,
  onAddItem,
  onDeleteItem,
  onResetDefaults,
  fullAppMode = false,
  onToggleFullAppMode,
}) => {
  const [isExpanded, setIsExpanded] = useState(false);
  const [draftText, setDraftText] = useState('');
  const [isListening, setIsListening] = useState(false);
  const [partialSpeech, setPartialSpeech] = useState('');
  const [sampleIndex, setSampleIndex] = useState(0);
  const [voiceModeLabel, setVoiceModeLabel] = useState<string>('SpeechRecognizer (fa-IR)');
  const [snackbarMessage, setSnackbarMessage] = useState<string | null>(null);

  const inputRef = useRef<HTMLInputElement | null>(null);
  const voiceTimerRef = useRef<number | null>(null);
  const snackbarTimerRef = useRef<number | null>(null);
  const recognitionRef = useRef<any>(null);

  const showSnackbar = (msg: string) => {
    setSnackbarMessage(msg);
    if (snackbarTimerRef.current) {
      window.clearTimeout(snackbarTimerRef.current);
    }
    snackbarTimerRef.current = window.setTimeout(() => {
      setSnackbarMessage(null);
    }, 3800);
  };

  const commitItemWithAlarmCheck = (rawText: string) => {
    const clean = rawText.trim();
    if (!clean) return;
    const processed = TextProcessor.process(clean);
    onAddItem(clean);

    if (processed.category === 'REMINDER' && processed.extractedTime) {
      const formattedClock = formatPersianClockOnly(processed.extractedTime);
      showSnackbar(`آلارم یادآوری برای ساعت ${formattedClock} تنظیم شد`);
    }
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
      showSnackbar('قطعی ارتباط با اینترنت: لطفاً اتصال اینترنت خود را بررسی کرده و دوباره تلاش کنید.');
      return;
    }
    setVoiceModeLabel('SpeechRecognizer (fa-IR)');
    const targetPhrase = VOSK_PERSIAN_VOICE_SAMPLES[sampleIndex % VOSK_PERSIAN_VOICE_SAMPLES.length];
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
    }, 260);
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
      showSnackbar('خطا در اتصال به اینترنت: لطفاً اتصال اینترنت خود را بررسی کرده و دوباره تلاش کنید.');
      return;
    }

    // Try real Web Speech API (fa-IR) first; fall back seamlessly to simulated SpeechRecognizer stream
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
  const livePreview = activeText.trim() ? TextProcessor.process(activeText) : null;

  return (
    <div className="flex flex-col">
      {/* Context Bar Above Device */}
      <div className="flex items-center justify-between mb-3 px-1">
        <div dir="rtl" className="font-persian">
          <h2 className="text-sm font-semibold text-stone-900">
            اپلیکیشن یاد (Yaad MVP) · پیش‌نمایش زنده
          </h2>
          <p className="text-xs text-stone-500">
            روی دکمه میکروفون بزنید یا متن فارسی بنویسید تا دسته‌بندی و استخراج زمان انجام شود
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
          fullAppMode ? 'max-w-[560px] h-[720px]' : 'max-w-[420px] h-[660px]'
        }`}
      >
        {/* Minimal Android Status Bar */}
        <div className="flex items-center justify-between px-6 pt-3 pb-2 text-[11px] text-stone-400 font-mono-tabular select-none border-b border-stone-100">
          <span>۱۰:۴۲</span>
          <span className="font-persian text-stone-500 font-medium">
            یاد · {isListening ? voiceModeLabel : 'SpeechRecognizer آنلاین (fa-IR)'}
          </span>
          <span>100%</span>
        </div>

        {/* Main Screen: Simple Vertical RecyclerView / LazyColumn Timeline */}
        <div
          dir="rtl"
          className="flex-1 overflow-y-auto px-6 pt-5 pb-28 font-persian select-text"
        >
          {items.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center text-center">
              <p className="text-sm text-stone-400">بنویسید یا صحبت کنید...</p>
            </div>
          ) : (
            <div className="divide-y divide-stone-100">
              {items.map((item) => {
                const catMeta = CATEGORY_DEFINITIONS[item.category];
                return (
                  <div
                    key={item.id}
                    className="group py-4 first:pt-1 transition-colors flex items-start justify-between gap-3"
                  >
                    <div className="flex-1 min-w-0">
                      <p className="text-[15px] leading-relaxed text-stone-900 break-words">
                        {item.originalText}
                      </p>

                      {/* Unboxed Inline Metadata (Zero-Pill Discipline) */}
                      <div className="mt-1.5 flex flex-wrap items-center gap-1.5 text-xs text-stone-500">
                        <span
                          className={
                            item.category === 'REMINDER'
                              ? 'text-blue-700 font-medium'
                              : item.category === 'OCCASION'
                              ? 'text-amber-700 font-medium'
                              : item.category === 'IDEA'
                              ? 'text-emerald-700 font-medium'
                              : 'text-stone-500'
                          }
                        >
                          {catMeta.persianLabel}
                        </span>

                        {item.extractedTime && (
                          <>
                            <span aria-hidden="true">·</span>
                            <span className="text-stone-700">
                              {formatPersianTimestamp(item.extractedTime, item.createdAt)}
                            </span>
                          </>
                        )}

                        <span aria-hidden="true">·</span>
                        <span className="text-stone-400">
                          {formatPersianClockOnly(item.createdAt)}
                        </span>
                      </div>
                    </div>

                    <button
                      type="button"
                      onClick={() => onDeleteItem(item.id)}
                      className="opacity-0 group-hover:opacity-100 focus:opacity-100 p-2 text-stone-400 hover:text-red-600 transition-opacity rounded-lg cursor-pointer"
                      aria-label="حذف آیتم"
                      title="حذف"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Live Rule-Based NLP Preview Strip (when typing or speaking) */}
        {livePreview && (
          <div
            dir="rtl"
            className="px-5 py-2 bg-stone-50/95 border-t border-stone-100 text-[11px] font-persian text-stone-600 flex items-center justify-between gap-2"
          >
            <div className="flex items-center gap-1.5 truncate">
              <span className="text-stone-400">دسته‌بندی خودکار:</span>
              <span className="font-semibold text-stone-900">
                {CATEGORY_DEFINITIONS[livePreview.category].persianLabel} (
                {livePreview.category})
              </span>
              {livePreview.extractedTime && (
                <>
                  <span aria-hidden="true">·</span>
                  <span className="text-blue-700">
                    {formatPersianTimestamp(livePreview.extractedTime)}
                  </span>
                </>
              )}
            </div>
            <span className="font-mono-tabular text-[10px] text-stone-400 shrink-0">
              {livePreview.extractedTime
                ? `${toPersianDigits(livePreview.matchedTimeTokens.join(' '))}`
                : 'بدون زمان'}
            </span>
          </div>
        )}

        {/* Android Snackbar Confirmation (when REMINDER alarm is scheduled via AlarmScheduler) */}
        {snackbarMessage && (
          <div
            dir="rtl"
            className="mx-4 mb-2 px-4 py-2.5 bg-stone-900 text-white rounded-xl text-xs font-persian flex items-center justify-between gap-2 shadow-sm"
            role="status"
          >
            <span>{snackbarMessage}</span>
            <span className="text-[10px] font-mono-tabular text-stone-300 shrink-0">
              AlarmManager
            </span>
          </div>
        )}

        {/* Bottom Input Component: Smoothly Expandable FAB */}
        <div
          dir="rtl"
          className="px-5 pb-5 pt-2 bg-gradient-to-t from-white via-white to-white/80"
        >
          <form
            onSubmit={handleSubmit}
            className={`flex items-center bg-stone-100 transition-all duration-200 ease-out rounded-full ${
              isExpanded ? 'w-full px-2 py-1.5' : 'w-fit mx-auto p-1.5'
            }`}
          >
            {/* Microphone / Stop Button */}
            <button
              type="button"
              onClick={handleToggleVoice}
              className={`min-w-[44px] min-h-[44px] rounded-full flex items-center justify-center transition-colors shrink-0 cursor-pointer ${
                isListening
                  ? 'bg-red-600 text-white'
                  : 'bg-stone-900 text-white hover:bg-stone-800'
              }`}
              title={isListening ? 'توقف و ثبت گفتار' : 'شروع تشخیص گفتار فارسی (Vosk)'}
            >
              {isListening ? (
                <Square className="w-4 h-4 fill-current" />
              ) : (
                <Mic className="w-4 h-4" />
              )}
            </button>

            {!isExpanded ? (
              <button
                type="button"
                onClick={() => setIsExpanded(true)}
                className="px-3.5 py-2 text-xs font-persian text-stone-500 hover:text-stone-800 transition-colors whitespace-nowrap cursor-pointer"
              >
                بنویسید یا صحبت کنید...
              </button>
            ) : (
              <>
                <input
                  ref={inputRef}
                  type="text"
                  dir="rtl"
                  value={activeText}
                  onChange={(e) => {
                    if (isListening) stopVoiceCapture();
                    setDraftText(e.target.value);
                  }}
                  onBlur={() => {
                    if (!activeText.trim() && !isListening) {
                      setIsExpanded(false);
                    }
                  }}
                  placeholder={
                    isListening ? 'در حال شنیدن صدای شما...' : 'بنویسید یا صحبت کنید...'
                  }
                  className="flex-1 bg-transparent border-0 outline-none px-3 text-sm font-persian text-stone-900 placeholder:text-stone-400 min-w-0"
                />

                <button
                  type="submit"
                  disabled={!activeText.trim()}
                  className={`min-w-[40px] min-h-[40px] rounded-full flex items-center justify-center transition-colors shrink-0 cursor-pointer ${
                    activeText.trim()
                      ? 'bg-blue-700 text-white hover:bg-blue-800'
                      : 'text-stone-400 cursor-not-allowed'
                  }`}
                  title="ثبت در تایم‌لاین"
                >
                  <Send className="w-4 h-4 rotate-180" />
                </button>
              </>
            )}
          </form>
        </div>
      </div>

      {/* Quick Voice Utterance Bar Below Device */}
      <div
        className={`mt-3 px-1 mx-auto w-full ${
          fullAppMode ? 'max-w-[560px]' : 'max-w-[420px]'
        }`}
      >
        <div dir="rtl" className="flex items-center justify-between text-xs text-stone-500 mb-1.5 font-persian">
          <span className="inline-flex items-center gap-1.5 font-medium text-stone-700">
            <Volume2 className="w-3.5 h-3.5 text-blue-700" />
            <span>جملات نمونه صوتی (با یک کلیک به تایم‌لاین اضافه کنید):</span>
          </span>
        </div>
        <div dir="rtl" className="flex flex-wrap gap-1.5 font-persian">
          {VOSK_PERSIAN_VOICE_SAMPLES.map((sample) => (
            <button
              key={sample}
              type="button"
              onClick={() => commitItemWithAlarmCheck(sample)}
              className="px-2.5 py-1.5 text-xs bg-white hover:bg-stone-100 text-stone-700 border border-stone-200 rounded-lg transition-colors text-right cursor-pointer"
            >
              {sample}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
};
