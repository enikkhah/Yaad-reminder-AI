import React, { useState } from 'react';
import { Check, Copy, Download, ArrowRight, Terminal, Lock, Unlock } from 'lucide-react';
import { KOTLIN_MODULES, KotlinFileSpec, TERMINAL_MODEL_SCRIPT } from '../data/kotlinModules';

interface KotlinExplorerProps {
  activeStage: 1 | 2;
  stage2Unlocked: boolean;
  onSelectStage: (stage: 1 | 2) => void;
  onUnlockStage2: () => void;
}

export const KotlinExplorer: React.FC<KotlinExplorerProps> = ({
  activeStage,
  stage2Unlocked,
  onSelectStage,
  onUnlockStage2,
}) => {
  const stageFiles = KOTLIN_MODULES.filter((m) => m.stage === activeStage);
  const [selectedFileId, setSelectedFileId] = useState<string>(stageFiles[0]?.id ?? 'item-entity');
  const [copiedId, setCopiedId] = useState<string | null>(null);

  // Ensure selected file belongs to active stage
  const activeFile: KotlinFileSpec =
    stageFiles.find((f) => f.id === selectedFileId) ?? stageFiles[0];

  const handleCopy = async (text: string, id: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedId(id);
      window.setTimeout(() => setCopiedId(null), 1800);
    } catch {
      // Fallback copy via textarea
      const ta = document.createElement('textarea');
      ta.value = text;
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
      setCopiedId(id);
      window.setTimeout(() => setCopiedId(null), 1800);
    }
  };

  const handleDownloadFile = (file: KotlinFileSpec) => {
    const blob = new Blob([file.code], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = file.fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-5">
      {/* Stage Switcher & Confirmation Banner */}
      <div className="bg-white border border-stone-200 rounded-xl p-5">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 text-xs text-stone-500">
              <span>Modular Android Architecture</span>
              <span aria-hidden="true">·</span>
              <span>Kotlin + MVVM + Room + Vosk</span>
            </div>
            <h2 className="text-lg font-semibold text-stone-900 mt-1">
              {activeStage === 1
                ? '01. Room Database Layer & Static TextProcessor'
                : '02. MVVM Repository, ViewModel, Compose UI & Vosk Integration'}
            </h2>
          </div>

          {/* Interactive Segmented Control */}
          <div className="flex items-center gap-1 p-1 bg-stone-100 rounded-lg self-start sm:self-auto">
            <button
              type="button"
              onClick={() => {
                onSelectStage(1);
                setSelectedFileId('item-entity');
              }}
              className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                activeStage === 1
                  ? 'bg-white text-stone-900 shadow-xs'
                  : 'text-stone-600 hover:text-stone-900'
              }`}
            >
              Stage 1: Room &amp; NLP (4 files)
            </button>
            <button
              type="button"
              onClick={() => {
                onUnlockStage2();
                onSelectStage(2);
                setSelectedFileId('item-repository');
              }}
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-md transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                activeStage === 2
                  ? 'bg-white text-stone-900 shadow-xs'
                  : 'text-stone-600 hover:text-stone-900'
              }`}
            >
              {stage2Unlocked ? (
                <Unlock className="w-3 h-3 text-emerald-700" />
              ) : (
                <Lock className="w-3 h-3 text-stone-400" />
              )}
              <span>Stage 2: UI, Vosk &amp; Gradle (7 files)</span>
            </button>
          </div>
        </div>

        {/* Stage 1 Confirmation Gate Banner */}
        {activeStage === 1 && (
          <div className="mt-4 pt-4 border-t border-stone-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <p className="text-xs text-stone-600 leading-relaxed">
              Per your execution instruction, Stage 1 presents only the <strong>Room Database Layer</strong> (<code className="font-mono-tabular text-stone-800">ItemEntity</code>, <code className="font-mono-tabular text-stone-800">ItemDao</code>, <code className="font-mono-tabular text-stone-800">YaadDatabase</code>) and the <strong>TextProcessor</strong> (Persian Regex &amp; Keyword classification). Ready for the UI and Vosk modules?
            </p>
            <button
              type="button"
              onClick={() => {
                onUnlockStage2();
                onSelectStage(2);
                setSelectedFileId('timeline-compose-ui');
              }}
              className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-medium text-white bg-blue-700 hover:bg-blue-800 rounded-lg transition-colors whitespace-nowrap shrink-0 cursor-pointer"
            >
              <span>Confirm Stage 1 &amp; Unlock UI + Vosk</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
          </div>
        )}
      </div>

      {/* File Selector Tabs + Active Kotlin File Viewer */}
      <div className="bg-white border border-stone-200 rounded-xl overflow-hidden">
        {/* Horizontal File Tab Bar */}
        <div className="flex items-center gap-1 px-4 pt-3 border-b border-stone-200 overflow-x-auto bg-stone-50/70">
          {stageFiles.map((file) => {
            const isSelected = file.id === activeFile.id;
            return (
              <button
                key={file.id}
                type="button"
                onClick={() => setSelectedFileId(file.id)}
                className={`px-3.5 py-2.5 text-xs font-mono-tabular font-medium border-b-2 transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                  isSelected
                    ? 'border-blue-700 text-blue-700 bg-white rounded-t-lg'
                    : 'border-transparent text-stone-600 hover:text-stone-900'
                }`}
              >
                {file.fileName}
              </button>
            );
          })}
        </div>

        {/* File Metadata & Action Header */}
        <div className="p-5 border-b border-stone-100 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <div className="flex flex-wrap items-center gap-2 text-xs text-stone-500">
              <span className="font-semibold text-stone-800">{activeFile.layer}</span>
              <span aria-hidden="true">·</span>
              <span className="font-mono-tabular text-stone-500">{activeFile.packagePath}</span>
            </div>
            <p className="text-xs text-stone-600 mt-1">{activeFile.summary}</p>
            <div className="mt-2 flex flex-wrap items-center gap-2 text-[11px] text-stone-500">
              {activeFile.highlights.map((h, idx) => (
                <React.Fragment key={h}>
                  {idx > 0 && <span aria-hidden="true">·</span>}
                  <span>{h}</span>
                </React.Fragment>
              ))}
            </div>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <button
              type="button"
              onClick={() => handleCopy(activeFile.code, activeFile.id)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-stone-700 bg-stone-100 hover:bg-stone-200 rounded-lg transition-colors whitespace-nowrap shrink-0 cursor-pointer"
            >
              {copiedId === activeFile.id ? (
                <>
                  <Check className="w-3.5 h-3.5 text-emerald-700" />
                  <span>Copied</span>
                </>
              ) : (
                <>
                  <Copy className="w-3.5 h-3.5" />
                  <span>Copy Kotlin</span>
                </>
              )}
            </button>

            <button
              type="button"
              onClick={() => handleDownloadFile(activeFile)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-white bg-stone-900 hover:bg-stone-800 rounded-lg transition-colors whitespace-nowrap shrink-0 cursor-pointer"
            >
              <Download className="w-3.5 h-3.5" />
              <span>Download {activeFile.fileName}</span>
            </button>
          </div>
        </div>

        {/* Source Code Pre Block */}
        <div className="bg-[#0F172A] text-stone-100 p-5 overflow-x-auto max-h-[540px] overflow-y-auto">
          <pre className="text-xs font-mono-tabular leading-relaxed">
            <code>{activeFile.code}</code>
          </pre>
        </div>
      </div>

      {/* Vosk Model Terminal Command Card (Always visible in Stage 2 or when inspecting build automation) */}
      <div className="bg-white border border-stone-200 rounded-xl p-5">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-3">
          <div className="flex items-center gap-2">
            <Terminal className="w-4 h-4 text-stone-700" />
            <h3 className="text-sm font-semibold text-stone-900">
              Vosk Persian Model Asset Automation (Android Studio Terminal)
            </h3>
          </div>
          <button
            type="button"
            onClick={() => handleCopy(TERMINAL_MODEL_SCRIPT, 'terminal-script')}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-stone-700 bg-stone-100 hover:bg-stone-200 rounded-lg transition-colors whitespace-nowrap shrink-0 cursor-pointer"
          >
            {copiedId === 'terminal-script' ? (
              <>
                <Check className="w-3.5 h-3.5 text-emerald-700" />
                <span>Copied Command</span>
              </>
            ) : (
              <>
                <Copy className="w-3.5 h-3.5" />
                <span>Copy Terminal Command</span>
              </>
            )}
          </button>
        </div>
        <p className="text-xs text-stone-600 mb-3">
          In addition to the automatic <code className="font-mono-tabular">downloadVoskPersianModel</code> Gradle task in <code className="font-mono-tabular">app/build.gradle.kts</code>, you can paste this exact one-liner into the Android Studio terminal to download, unzip, and place <code className="font-mono-tabular">vosk-model-small-fa-0.4</code> into <code className="font-mono-tabular">app/src/main/assets/model-fa</code>:
        </p>
        <pre className="p-3.5 bg-stone-900 text-stone-100 rounded-lg text-xs font-mono-tabular overflow-x-auto leading-relaxed">
          <code>{TERMINAL_MODEL_SCRIPT}</code>
        </pre>
      </div>
    </div>
  );
};
