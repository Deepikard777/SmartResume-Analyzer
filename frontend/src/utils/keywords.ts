import type { KeywordCoverage } from '../types/api';

/**
 * Pure presentation logic for the keyword-frequency view.
 *
 * Kept free of React and of any data fetching so it can be unit-tested directly
 * (see frontend/tests/keywords.test.ts). It never invents data: every value comes
 * from the `keywordCoverage` array returned by the analysis endpoint, and an older
 * analysis that predates that field simply yields an empty table.
 */

export type KeywordStatus = 'repeated' | 'matched' | 'missing' | 'resume-only';

export interface KeywordRow {
  keyword: string;
  /** Whole-word occurrences in the job description. */
  jobDescriptionCount: number;
  /** Whole-word occurrences in the resume. */
  resumeCount: number;
  matched: boolean;
  flagged: boolean;
  flagReason: string | null;
  inJobDescription: boolean;
  status: KeywordStatus;
  statusLabel: string;
}

export interface KeywordSummary {
  /** Number of rows shown. */
  total: number;
  /** Rows that came from the job description (the coverage denominator). */
  fromJobDescription: number;
  matched: number;
  missing: number;
  repeated: number;
  /** Percentage of job-description keywords present in the resume, 0–100. */
  coveragePercent: number;
  /** Highest resume-side count, used to scale the frequency bars. */
  maxResumeCount: number;
}

const STATUS_LABELS: Record<KeywordStatus, string> = {
  repeated: 'Repeated',
  matched: 'Matched',
  missing: 'Missing',
  'resume-only': 'In resume only',
};

function toInt(value: number | null | undefined): number {
  return typeof value === 'number' && Number.isFinite(value) ? Math.max(0, Math.trunc(value)) : 0;
}

/**
 * One row per keyword, in the order the backend returned them (job-description
 * emphasis first). Repetition takes precedence over "matched" as the displayed
 * status: a term can be both present and flagged, and the flag is the actionable
 * signal. Being repeated is NOT treated as an error — the caller renders it as an
 * advisory tone, and the underlying counts are always shown.
 */
export function buildKeywordRows(coverage: KeywordCoverage[] | null | undefined): KeywordRow[] {
  if (!coverage || coverage.length === 0) return [];

  const seen = new Set<string>();
  const rows: KeywordRow[] = [];

  for (const entry of coverage) {
    const keyword = (entry?.keyword ?? '').trim();
    if (!keyword || seen.has(keyword)) continue;
    seen.add(keyword);

    const matched = entry.matched === true;
    const flagged = entry.flagged === true;
    const inJobDescription = entry.inJobDescription === true;

    let status: KeywordStatus;
    if (flagged) status = 'repeated';
    else if (matched && inJobDescription) status = 'matched';
    else if (matched) status = 'resume-only';
    else status = 'missing';

    rows.push({
      keyword,
      jobDescriptionCount: toInt(entry.jobDescriptionCount),
      resumeCount: toInt(entry.resumeCount),
      matched,
      flagged,
      flagReason: entry.flagReason ?? null,
      inJobDescription,
      status,
      statusLabel: STATUS_LABELS[status],
    });
  }

  return rows;
}

export function summariseKeywords(rows: KeywordRow[]): KeywordSummary {
  const fromJobDescription = rows.filter((row) => row.inJobDescription).length;
  const matched = rows.filter((row) => row.matched && row.inJobDescription).length;
  const missing = rows.filter((row) => row.inJobDescription && !row.matched).length;
  const repeated = rows.filter((row) => row.flagged).length;
  const maxResumeCount = rows.reduce((max, row) => Math.max(max, row.resumeCount), 0);

  return {
    total: rows.length,
    fromJobDescription,
    matched,
    missing,
    repeated,
    coveragePercent:
      fromJobDescription === 0 ? 0 : Math.round((matched / fromJobDescription) * 100),
    maxResumeCount,
  };
}

/** Bar length as a percentage of the largest observed count (0 when there is none). */
export function relativeBarPercent(value: number, max: number): number {
  if (!Number.isFinite(value) || !Number.isFinite(max) || max <= 0) return 0;
  return Math.max(0, Math.min(100, Math.round((value / max) * 100)));
}

export type KeywordTone = 'success' | 'warning' | 'danger' | 'neutral';

/**
 * Visual tone per status. Repetition is deliberately only a warning, never an
 * error: repeating a core technical term is normal and is not by itself a fault.
 */
export function statusTone(status: KeywordStatus): KeywordTone {
  switch (status) {
    case 'matched':
      return 'success';
    case 'repeated':
      return 'warning';
    case 'missing':
      return 'danger';
    default:
      return 'neutral';
  }
}

/** Split a list into the part that is shown and the count that was elided. */
export function splitForDisplay(
  values: string[] | null | undefined,
  max: number,
): { shown: string[]; hidden: number } {
  const list = values ?? [];
  if (list.length <= max) return { shown: list, hidden: 0 };
  return { shown: list.slice(0, max), hidden: list.length - max };
}
