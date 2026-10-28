import React, { useState } from 'react';
import { Play, Plus } from 'lucide-react';
import {
  CATEGORY_DEFINITIONS,
  ItemCategory,
  TextProcessor,
  formatPersianTimestamp,
} from '../nlp/textProcessor';

interface NlpTestBenchProps {
  onInsertProcessedItem: (rawText: string) => void;
}

const TEST_CASES = [
  {
    label: 'Reminder + Tomorrow + Hour',
    persian: 'یادم باشه فردا ساعت ۸ صبح بیدار شم',
  },
  {
    label: 'Occasion + Evening Hour',
    persian: 'تولد علی فردا ساعت ۷ شب یادم نره تبریک بگم',
  },
  {
    label: 'Idea + Keyword Match',
    persian: 'ایده و طرح اولیه برای ساخت اپلیکیشن مینیمال آفلاین',
  },
  {
    label: 'Day After Tomorrow + Half Hour',
    persian: 'یادآوری جلسه کاری پس‌فردا ساعت ۱۴:۳۰',
  },
  {
    label: 'Uncategorized Fallback',
    persian: 'خرید نان سنگک و پنیر لیقوان در مسیر برگشت',
  },
];

export const NlpTestBench: React.FC<NlpTestBenchProps> = ({ onInsertProcessedItem }) => {
  const [testInput, setTestInput] = useState(TEST_CASES[0].persian);
  const referenceNow = Date.now();
  const result = TextProcessor.process(testInput, referenceNow);

  return (
    <div className="space-y-6">
      {/* Header & Input Tester */}
      <div className="bg-white border border-stone-200 rounded-xl p-5">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-4">
          <div>
            <h3 className="text-base font-semibold text-stone-900">
              Static Processing Brain — TextProcessor Inspector
            </h3>
            <p className="text-xs text-stone-500 mt-0.5">
              Tests the exact deterministic keyword classification and Persian Regex time extraction logic from TextProcessor.kt
            </p>
          </div>
          <button
            type="button"
            onClick={() => {
              if (testInput.trim()) {
                onInsertProcessedItem(testInput.trim());
              }
            }}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 text-xs font-medium text-white bg-stone-900 hover:bg-stone-800 rounded-lg transition-colors whitespace-nowrap shrink-0 cursor-pointer"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Insert into Timeline</span>
          </button>
        </div>

        {/* Preset Test Buttons */}
        <div className="flex flex-wrap gap-1.5 mb-4">
          {TEST_CASES.map((tc) => (
            <button
              key={tc.label}
              type="button"
              onClick={() => setTestInput(tc.persian)}
              className={`px-2.5 py-1.5 text-xs font-medium rounded-lg border transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                testInput === tc.persian
                  ? 'bg-blue-700 text-white border-blue-700'
                  : 'bg-stone-50 text-stone-700 border-stone-200 hover:bg-stone-100'
              }`}
            >
              {tc.label}
            </button>
          ))}
        </div>

        {/* RTL Persian Input Box */}
        <div>
          <label
            htmlFor="nlp-test-input"
            className="block text-xs font-medium text-stone-600 mb-1.5"
          >
            Persian Input String (rawInput: String)
          </label>
          <input
            id="nlp-test-input"
            type="text"
            dir="rtl"
            value={testInput}
            onChange={(e) => setTestInput(e.target.value)}
            placeholder="جمله فارسی را برای تست دسته‌بندی و استخراج زمان وارد کنید..."
            className="w-full px-4 py-3 text-base font-persian bg-stone-50 border border-stone-200 rounded-lg text-stone-900 focus:outline-none focus:border-blue-700"
          />
        </div>

        {/* Output Breakdown Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-5 pt-5 border-t border-stone-100">
          <div>
            <div className="text-xs text-stone-500">Assigned Category</div>
            <div className="mt-1 font-mono-tabular text-sm font-semibold text-stone-900">
              {result.category} ·{' '}
              <span className="font-persian font-normal text-stone-700">
                {CATEGORY_DEFINITIONS[result.category].persianLabel}
              </span>
            </div>
            <div className="mt-1 text-xs text-stone-500 font-persian" dir="rtl">
              {result.matchedKeywords.length > 0
                ? `کلیدواژه منطبق: ${result.matchedKeywords.join(' ، ')}`
                : 'بدون تطابق کلیدواژه (پیش‌فرض UNCATEGORIZED)'}
            </div>
          </div>

          <div>
            <div className="text-xs text-stone-500">Regex Time Extraction</div>
            <div className="mt-1 font-persian text-sm font-semibold text-stone-900" dir="rtl">
              {result.matchedTimeTokens.length > 0
                ? result.matchedTimeTokens.join(' + ')
                : 'الگوی زمانی یافت نشد (null)'}
            </div>
            <div className="mt-1 text-xs text-stone-500 font-mono-tabular">
              {result.extractedTime
                ? `Epoch: ${result.extractedTime} ms`
                : 'extractedTime = null'}
            </div>
          </div>

          <div>
            <div className="text-xs text-stone-500">Resolved Persian Timestamp</div>
            <div className="mt-1 font-persian text-sm font-semibold text-blue-700" dir="rtl">
              {result.extractedTime
                ? formatPersianTimestamp(result.extractedTime, referenceNow)
                : 'بدون زمان یادآوری'}
            </div>
            <div className="mt-1 text-xs text-stone-500 font-mono-tabular">
              Normalized: &quot;{result.normalizedText}&quot;
            </div>
          </div>
        </div>
      </div>

      {/* Static Keyword Dictionary Matrix & Room Entity Output */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        <div className="bg-white border border-stone-200 rounded-xl p-5">
          <h4 className="text-sm font-semibold text-stone-900 mb-3">
            Static Keyword Classification Table
          </h4>
          <div className="divide-y divide-stone-100 text-xs">
            {[
              ItemCategory.REMINDER,
              ItemCategory.OCCASION,
              ItemCategory.IDEA,
              ItemCategory.UNCATEGORIZED,
            ].map((cat) => {
              const def = CATEGORY_DEFINITIONS[cat];
              const isMatched = result.category === cat;
              return (
                <div
                  key={cat}
                  className={`py-2.5 flex items-center justify-between gap-4 ${
                    isMatched ? 'text-blue-700 font-semibold' : 'text-stone-600'
                  }`}
                >
                  <div className="flex items-center gap-2 shrink-0">
                    <Play
                      className={`w-3 h-3 ${
                        isMatched ? 'opacity-100 text-blue-700' : 'opacity-0'
                      }`}
                    />
                    <span className="font-mono-tabular">{def.englishLabel}</span>
                    <span>·</span>
                    <span className="font-persian">{def.persianLabel}</span>
                  </div>
                  <div dir="rtl" className="font-persian text-right text-stone-700">
                    {def.keywords.length > 0
                      ? def.keywords.join(' ، ')
                      : 'Fallback (بدون کلیدواژه)'}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        <div className="bg-white border border-stone-200 rounded-xl p-5">
          <h4 className="text-sm font-semibold text-stone-900 mb-3">
            Generated Room ItemEntity Preview
          </h4>
          <pre className="p-3.5 bg-stone-900 text-stone-100 rounded-lg text-xs font-mono-tabular overflow-x-auto leading-relaxed">
{`ItemEntity(
    id = 0L, // Auto-generated by Room SQLite
    originalText = "${result.originalText}",
    category = ItemCategory.${result.category},
    extractedTime = ${result.extractedTime !== null ? `${result.extractedTime}L` : 'null'},
    createdAt = ${referenceNow}L
)`}
          </pre>
        </div>
      </div>
    </div>
  );
};
