/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import { Download } from 'lucide-react';
import { ItemCategory, ItemEntity, TextProcessor } from './nlp/textProcessor';
import { KOTLIN_MODULES, TERMINAL_MODEL_SCRIPT } from './data/kotlinModules';
import { AndroidSimulator } from './components/AndroidSimulator';
import { KotlinExplorer } from './components/KotlinExplorer';
import { NlpTestBench } from './components/NlpTestBench';

type WorkspaceTab = 'stage1' | 'stage2' | 'nlp-bench' | 'full-app';

const STORAGE_KEY = 'yaad_reminder_timeline_shamsi_v2';

function createInitialTimeline(): ItemEntity[] {
  const now = Date.now();
  return [
    {
      id: 1,
      originalText: 'یادم باشه ۲ ساعت دیگه داروی سرماخوردگی رو بخورم',
      category: ItemCategory.REMINDER,
      extractedTime: now + 2 * 60 * 60 * 1000,
      createdAt: now - 25 * 60 * 1000,
    },
    {
      id: 2,
      originalText: 'یادم باشه فردا ساعت ۸ صبح بیدار شم و ورزش کنم',
      category: ItemCategory.REMINDER,
      extractedTime: TextProcessor.process('یادم باشه فردا ساعت ۸ صبح بیدار شم', now).extractedTime,
      createdAt: now - 45 * 60 * 1000,
    },
    {
      id: 3,
      originalText: 'تبریک تولد سارا ۱۵ آبان ساعت ۶ عصر',
      category: ItemCategory.OCCASION,
      extractedTime: TextProcessor.process('تبریک تولد سارا ۱۵ آبان ساعت ۶ عصر', now).extractedTime,
      createdAt: now - 90 * 60 * 1000,
    },
    {
      id: 4,
      originalText: 'ایده برای طراحی رابط کاربری مینیمال با تقویم شمسی و خط زمان اکنون',
      category: ItemCategory.IDEA,
      extractedTime: null,
      createdAt: now - 140 * 60 * 1000,
    },
    {
      id: 5,
      originalText: 'تماس با دفتر بیمه (یادآور انجام‌شده امروز)',
      category: ItemCategory.REMINDER,
      extractedTime: now - 50 * 60 * 1000,
      createdAt: now - 180 * 60 * 1000,
    },
  ];
}

export default function App() {
  const [activeTab, setActiveTab] = useState<WorkspaceTab>('stage2');
  const [stage2Unlocked, setStage2Unlocked] = useState<boolean>(true);
  const [items, setItems] = useState<ItemEntity[]>(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) {
        const parsed = JSON.parse(saved);
        if (Array.isArray(parsed) && parsed.length > 0) {
          return parsed;
        }
      }
    } catch {
      // ignore storage errors
    }
    return createInitialTimeline();
  });

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
    } catch {
      // ignore storage errors
    }
  }, [items]);

  const handleAddItem = (rawText: string, explicitShamsiMillis?: number | null) => {
    const clean = rawText.trim();
    if (!clean) return;

    const now = Date.now();
    const processed = TextProcessor.process(clean, now);
    const resolvedTime =
      explicitShamsiMillis !== undefined && explicitShamsiMillis !== null
        ? explicitShamsiMillis
        : processed.extractedTime;

    const resolvedCategory =
      explicitShamsiMillis != null && processed.category === ItemCategory.UNCATEGORIZED
        ? ItemCategory.REMINDER
        : processed.category;

    const newItem: ItemEntity = {
      id: now,
      originalText: processed.originalText,
      category: resolvedCategory,
      extractedTime: resolvedTime,
      createdAt: now,
    };

    setItems((prev) => [newItem, ...prev]);
  };

  const handleUpdateItemAlarm = (id: number, newEpochMillis: number | null) => {
    setItems((prev) =>
      prev.map((item) =>
        item.id === id
          ? {
              ...item,
              extractedTime: newEpochMillis,
              category:
                newEpochMillis !== null && item.category === ItemCategory.UNCATEGORIZED
                  ? ItemCategory.REMINDER
                  : item.category,
            }
          : item
      )
    );
  };

  const handleDeleteItem = (id: number) => {
    setItems((prev) => prev.filter((item) => item.id !== id));
  };

  const handleResetDefaults = () => {
    const defaults = createInitialTimeline();
    setItems(defaults);
  };

  const handleExportAllKotlin = () => {
    const sections = KOTLIN_MODULES.map(
      (mod) =>
        `// ============================================================================\n` +
        `// FILE: ${mod.packagePath}\n` +
        `// LAYER: ${mod.layer} (Stage ${mod.stage})\n` +
        `// ============================================================================\n\n` +
        `${mod.code}\n`
    ).join('\n\n');

    const fullBundle =
      `// YAAD REMINDER AI — COMPLETE NATIVE ANDROID PROJECT (KOTLIN + MVVM + ROOM + SHAMSI CALENDAR)\n\n` +
      sections +
      `\n// ============================================================================\n` +
      `// GRADLE BUILD COMMAND\n` +
      `// ============================================================================\n` +
      `${TERMINAL_MODEL_SCRIPT}\n`;

    const blob = new Blob([fullBundle], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'yaad-reminder-ai-complete-android-mvp.kt.txt';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="min-h-screen flex flex-col bg-[#FAF9F6] text-stone-900">
      {/* Strict 3-Zone Top Bar Contract */}
      <header className="flex items-center justify-between gap-8 px-6 py-4 bg-white border-b border-stone-200 sticky top-0 z-30">
        {/* Zone 1: Single text element wordmark */}
        <a
          href="#top"
          onClick={(e) => {
            e.preventDefault();
            setActiveTab('full-app');
          }}
          className="text-lg font-bold tracking-tight text-stone-900 whitespace-nowrap shrink-0"
        >
          Yaad reminder AI
        </a>

        {/* Zone 2: 4 concise single-line navigation links */}
        <nav className="hidden md:flex items-center gap-6 text-sm font-medium text-stone-600">
          <button
            type="button"
            onClick={() => setActiveTab('full-app')}
            className={`hover:text-stone-900 transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
              activeTab === 'full-app'
                ? 'text-stone-900 underline underline-offset-8 decoration-2 decoration-blue-700'
                : ''
            }`}
          >
            Live App
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('stage1')}
            className={`hover:text-stone-900 transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
              activeTab === 'stage1'
                ? 'text-stone-900 underline underline-offset-8 decoration-2 decoration-blue-700'
                : ''
            }`}
          >
            Room &amp; Shamsi NLP
          </button>
          <button
            type="button"
            onClick={() => {
              setStage2Unlocked(true);
              setActiveTab('stage2');
            }}
            className={`hover:text-stone-900 transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
              activeTab === 'stage2'
                ? 'text-stone-900 underline underline-offset-8 decoration-2 decoration-blue-700'
                : ''
            }`}
          >
            UI, Speech &amp; Gradle
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('nlp-bench')}
            className={`hover:text-stone-900 transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
              activeTab === 'nlp-bench'
                ? 'text-stone-900 underline underline-offset-8 decoration-2 decoration-blue-700'
                : ''
            }`}
          >
            NLP Test Bench
          </button>
        </nav>

        {/* Zone 3: 1 primary action */}
        <div className="flex items-center gap-3 shrink-0">
          <button
            type="button"
            onClick={handleExportAllKotlin}
            className="inline-flex items-center gap-2 px-4 py-2 text-xs font-medium text-white bg-stone-900 rounded-lg hover:bg-stone-800 transition-colors whitespace-nowrap shrink-0 cursor-pointer"
          >
            <Download className="w-3.5 h-3.5" />
            <span>Export Full Android Code</span>
          </button>
        </div>
      </header>

      {/* Main 1440px Desktop Workspace Container */}
      <main className="flex-1 max-w-[1400px] w-full mx-auto px-6 py-8">
        {/* Mobile Navigation Switcher */}
        <div className="flex md:hidden items-center gap-1 p-1 mb-6 bg-stone-200/70 rounded-lg overflow-x-auto">
          <button
            type="button"
            onClick={() => setActiveTab('full-app')}
            className={`flex-1 px-3 py-1.5 text-xs font-medium rounded-md whitespace-nowrap shrink-0 ${
              activeTab === 'full-app' ? 'bg-white text-stone-900 shadow-xs' : 'text-stone-600'
            }`}
          >
            Live App
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('stage1')}
            className={`flex-1 px-3 py-1.5 text-xs font-medium rounded-md whitespace-nowrap shrink-0 ${
              activeTab === 'stage1' ? 'bg-white text-stone-900 shadow-xs' : 'text-stone-600'
            }`}
          >
            Room &amp; Shamsi
          </button>
          <button
            type="button"
            onClick={() => {
              setStage2Unlocked(true);
              setActiveTab('stage2');
            }}
            className={`flex-1 px-3 py-1.5 text-xs font-medium rounded-md whitespace-nowrap shrink-0 ${
              activeTab === 'stage2' ? 'bg-white text-stone-900 shadow-xs' : 'text-stone-600'
            }`}
          >
            UI &amp; Speech
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('nlp-bench')}
            className={`flex-1 px-3 py-1.5 text-xs font-medium rounded-md whitespace-nowrap shrink-0 ${
              activeTab === 'nlp-bench' ? 'bg-white text-stone-900 shadow-xs' : 'text-stone-600'
            }`}
          >
            NLP Bench
          </button>
        </div>

        {activeTab === 'full-app' ? (
          /* Centered Full App View */
          <div className="max-w-[580px] mx-auto">
            <AndroidSimulator
              items={items}
              onAddItem={handleAddItem}
              onUpdateItemAlarm={handleUpdateItemAlarm}
              onDeleteItem={handleDeleteItem}
              onResetDefaults={handleResetDefaults}
              fullAppMode={true}
              onToggleFullAppMode={() => setActiveTab('stage2')}
            />
          </div>
        ) : (
          /* 12-Column Split: Left Interactive Android RTL Device Preview (5 cols) + Right Architecture & NLP Studio (7 cols) */
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            {/* Left Column: Live Minimalist Android Device Simulator */}
            <div className="lg:col-span-5 lg:sticky lg:top-24">
              <AndroidSimulator
                items={items}
                onAddItem={handleAddItem}
                onUpdateItemAlarm={handleUpdateItemAlarm}
                onDeleteItem={handleDeleteItem}
                onResetDefaults={handleResetDefaults}
                fullAppMode={false}
                onToggleFullAppMode={() => setActiveTab('full-app')}
              />
            </div>

            {/* Right Column: Complete Kotlin Source Explorer (Stage 1 + Stage 2) or Live Persian NLP Test Bench */}
            <div className="lg:col-span-7 space-y-6">
              {activeTab === 'nlp-bench' ? (
                <NlpTestBench onInsertProcessedItem={(txt) => handleAddItem(txt)} />
              ) : (
                <KotlinExplorer
                  activeStage={activeTab === 'stage2' ? 2 : 1}
                  stage2Unlocked={stage2Unlocked}
                  onSelectStage={(stage) => setActiveTab(stage === 2 ? 'stage2' : 'stage1')}
                  onUnlockStage2={() => setStage2Unlocked(true)}
                />
              )}
            </div>
          </div>
        )}
      </main>

      {/* Clean Minimal Footer */}
      <footer className="border-t border-stone-200 bg-white px-6 py-4 mt-12">
        <div className="max-w-[1400px] mx-auto flex flex-col sm:flex-row items-center justify-between gap-2 text-xs text-stone-500">
          <span>Yaad reminder AI · Persian Voice &amp; Shamsi Calendar Reminder App</span>
          <span>Kotlin · MVVM · Room · SpeechRecognizer (fa-IR) · Jalali Calendar · Jetpack Compose</span>
        </div>
      </footer>
    </div>
  );
}
