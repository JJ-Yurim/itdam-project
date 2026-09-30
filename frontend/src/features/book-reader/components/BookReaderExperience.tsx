import { useState } from 'react';
import { Link } from 'react-router-dom';

import type { BookContentResponse } from '@/features/library';

import { BookFlip } from './BookFlip';

const READER_SCALES = [0.9, 1, 1.1] as const;
const READER_FONTS = [
  {
    id: 'maru',
    label: '마루부리',
    shortLabel: '마루',
    family: "'Maru Buri', serif",
  },
  {
    id: 'myeongjo',
    label: '명조',
    shortLabel: '명조',
    family: "'Batang', 'Noto Serif KR', serif",
  },
  {
    id: 'pretendard',
    label: '프리텐다드 고딕',
    shortLabel: '고딕',
    family: "'Pretendard Variable', Pretendard, sans-serif",
  },
] as const;

type ReaderFontId = (typeof READER_FONTS)[number]['id'];

interface BookReaderExperienceProps {
  title: string;
  body: string;
  contents: BookContentResponse[];
  bodyPages?: readonly string[];
  sources?: BookContentResponse[];
  backTo?: string;
  onBack?: () => void;
  turnPrompt?: string;
  completionLabel?: string;
  onComplete?: () => void;
  onReaderInteract?: () => void;
}

export function BookReaderExperience({
  title,
  body,
  contents,
  bodyPages,
  sources,
  backTo,
  onBack,
  turnPrompt,
  completionLabel,
  onComplete,
  onReaderInteract,
}: BookReaderExperienceProps) {
  const [scaleIndex, setScaleIndex] = useState(1);
  const [fontId, setFontId] = useState<ReaderFontId>('maru');
  const [backgroundTheme, setBackgroundTheme] = useState<'light' | 'dim'>('light');
  const selectedFont = READER_FONTS.find((font) => font.id === fontId) ?? READER_FONTS[0];
  const backLabel = '공동 도서 정보로 돌아가기';

  return (
    <article className="reader-room" data-background-theme={backgroundTheme}>
      <header className="reader-room-header">
        <div className="reader-room-breadcrumb">
          {backTo ? (
            <Link to={backTo} aria-label={backLabel} className="reader-room-back">
              <span aria-hidden="true">←</span>
            </Link>
          ) : (
            <button
              type="button"
              aria-label={backLabel}
              className="reader-room-back"
              onClick={onBack}
            >
              <span aria-hidden="true">←</span>
            </button>
          )}
          <h1 className="reader-room-title">{title}</h1>
          <span aria-hidden="true" className="reader-room-divider" />
          <span className="reader-room-chapter">공동 소설집</span>
        </div>

        <div className="reader-room-tools">
          <label className="reader-font-select" title={`본문 글꼴: ${selectedFont.label}`}>
            <span aria-hidden="true" style={{ fontFamily: selectedFont.family }}>
              가
            </span>
            <span aria-hidden="true">{selectedFont.shortLabel}</span>
            <select
              aria-label="본문 글꼴"
              value={fontId}
              onChange={(event) => setFontId(event.currentTarget.value as ReaderFontId)}
            >
              {READER_FONTS.map((font) => (
                <option key={font.id} value={font.id}>
                  {font.label}
                </option>
              ))}
            </select>
          </label>
          <button
            type="button"
            aria-label={`본문 배율 ${Math.round(READER_SCALES[scaleIndex] * 100)}%`}
            onClick={() => setScaleIndex((current) => (current + 1) % READER_SCALES.length)}
          >
            <span aria-hidden="true">{Math.round(READER_SCALES[scaleIndex] * 100)}%</span>
            <span>배율</span>
          </button>
          <button
            type="button"
            aria-label={
              backgroundTheme === 'light' ? '어두운 독서 배경으로 변경' : '밝은 독서 배경으로 변경'
            }
            aria-pressed={backgroundTheme === 'dim'}
            onClick={() => setBackgroundTheme((current) => (current === 'light' ? 'dim' : 'light'))}
          >
            <span aria-hidden="true">{backgroundTheme === 'light' ? '☾' : '☀'}</span>
            <span>테마</span>
          </button>
        </div>
      </header>

      <div className="reader-room-body">
        <main className="reader-room-reader">
          <BookFlip
            body={body}
            contents={contents}
            bodyPages={bodyPages}
            sources={sources}
            fontScale={READER_SCALES[scaleIndex]}
            fontFamily={selectedFont.family}
            turnPrompt={turnPrompt}
            completionLabel={completionLabel}
            onComplete={onComplete}
            onInteract={onReaderInteract}
          />
        </main>
      </div>
    </article>
  );
}
