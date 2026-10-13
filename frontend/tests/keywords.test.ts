import { test, describe } from 'node:test';
import assert from 'node:assert/strict';

import {
  buildKeywordRows,
  relativeBarPercent,
  splitForDisplay,
  statusTone,
  summariseKeywords,
} from '../src/utils/keywords.ts';

/**
 * Unit tests for the keyword-frequency presentation logic (stage C).
 *
 * These exercise the pure helpers directly. They assert that a legitimate repeated
 * technical term is reported as a count plus an advisory flag, never as an error, and
 * that missing/absent data degrades to an empty view rather than fabricated rows.
 */

const row = (over: Record<string, unknown> = {}) => ({
  keyword: 'java',
  jobDescriptionCount: 1,
  resumeCount: 1,
  matched: true,
  flagged: false,
  flagReason: null,
  inJobDescription: true,
  ...over,
});

describe('buildKeywordRows', () => {
  test('returns an empty array for missing or empty coverage', () => {
    assert.deepEqual(buildKeywordRows(null), []);
    assert.deepEqual(buildKeywordRows(undefined), []);
    assert.deepEqual(buildKeywordRows([]), []);
  });

  test('marks a keyword present in both as matched', () => {
    const rows = buildKeywordRows([row()] as never);
    assert.equal(rows.length, 1);
    assert.equal(rows[0].status, 'matched');
    assert.equal(rows[0].statusLabel, 'Matched');
  });

  test('marks a required keyword absent from the resume as missing', () => {
    const rows = buildKeywordRows([
      row({ keyword: 'docker', resumeCount: 0, matched: false }),
    ] as never);
    assert.equal(rows[0].status, 'missing');
    assert.equal(rows[0].jobDescriptionCount, 1);
    assert.equal(rows[0].resumeCount, 0);
  });

  test('repetition is reported as an advisory status, not an error', () => {
    const rows = buildKeywordRows([
      row({ resumeCount: 7, flagged: true, flagReason: 'Appears 7 times.' }),
    ] as never);

    assert.equal(rows[0].status, 'repeated');
    assert.equal(rows[0].statusLabel, 'Repeated');
    // The raw count is preserved so the user can judge it themselves.
    assert.equal(rows[0].resumeCount, 7);
    assert.equal(rows[0].flagReason, 'Appears 7 times.');
    // Tone is a warning, never a danger/error.
    assert.equal(statusTone(rows[0].status), 'warning');
  });

  test('repetition takes precedence over matched as the displayed status', () => {
    const rows = buildKeywordRows([row({ matched: true, flagged: true })] as never);
    assert.equal(rows[0].status, 'repeated');
    assert.equal(rows[0].matched, true);
  });

  test('a keyword only in the resume is distinguished from a job-description keyword', () => {
    const rows = buildKeywordRows([
      row({ keyword: 'git', jobDescriptionCount: 0, inJobDescription: false, matched: true }),
    ] as never);
    assert.equal(rows[0].status, 'resume-only');
    assert.equal(rows[0].statusLabel, 'In resume only');
  });

  test('duplicate keywords are collapsed and blank keywords skipped', () => {
    const rows = buildKeywordRows([
      row({ keyword: 'java' }),
      row({ keyword: 'java', resumeCount: 5 }),
      row({ keyword: '   ' }),
    ] as never);
    assert.equal(rows.length, 1);
    assert.equal(rows[0].keyword, 'java');
  });

  test('negative or non-numeric counts are clamped to zero rather than shown as-is', () => {
    const rows = buildKeywordRows([
      row({ jobDescriptionCount: -3, resumeCount: Number.NaN as unknown as number }),
    ] as never);
    assert.equal(rows[0].jobDescriptionCount, 0);
    assert.equal(rows[0].resumeCount, 0);
  });
});

describe('summariseKeywords', () => {
  test('computes coverage over job-description keywords only', () => {
    const rows = buildKeywordRows([
      row({ keyword: 'java', inJobDescription: true, matched: true }),
      row({ keyword: 'docker', inJobDescription: true, matched: false, resumeCount: 0 }),
      row({ keyword: 'git', inJobDescription: false, matched: true }),
    ] as never);

    const summary = summariseKeywords(rows);
    assert.equal(summary.total, 3);
    assert.equal(summary.fromJobDescription, 2);
    assert.equal(summary.matched, 1);
    assert.equal(summary.missing, 1);
    assert.equal(summary.coveragePercent, 50);
  });

  test('coverage is zero rather than NaN when there are no job-description keywords', () => {
    assert.equal(summariseKeywords([]).coveragePercent, 0);
  });

  test('reports the largest resume-side count for bar scaling', () => {
    const rows = buildKeywordRows([
      row({ keyword: 'java', resumeCount: 3 }),
      row({ keyword: 'sql', resumeCount: 9 }),
    ] as never);
    assert.equal(summariseKeywords(rows).maxResumeCount, 9);
  });

  test('counts flagged rows even when they are not job-description keywords', () => {
    const rows = buildKeywordRows([
      row({ keyword: 'java', flagged: true, resumeCount: 8 }),
      row({ keyword: 'git', flagged: true, resumeCount: 6, inJobDescription: false }),
    ] as never);
    assert.equal(summariseKeywords(rows).repeated, 2);
  });
});

describe('relativeBarPercent', () => {
  test('scales against the maximum', () => {
    assert.equal(relativeBarPercent(5, 10), 50);
    assert.equal(relativeBarPercent(10, 10), 100);
  });

  test('is zero when there is nothing to scale against', () => {
    assert.equal(relativeBarPercent(3, 0), 0);
    assert.equal(relativeBarPercent(0, 0), 0);
  });

  test('never exceeds 100 or goes below zero', () => {
    assert.equal(relativeBarPercent(20, 10), 100);
    assert.equal(relativeBarPercent(-5, 10), 0);
  });
});

describe('statusTone', () => {
  test('maps each status to its tone', () => {
    assert.equal(statusTone('matched'), 'success');
    assert.equal(statusTone('missing'), 'danger');
    assert.equal(statusTone('repeated'), 'warning');
    assert.equal(statusTone('resume-only'), 'neutral');
  });
});

describe('splitForDisplay', () => {
  test('leaves short lists untouched', () => {
    assert.deepEqual(splitForDisplay(['a', 'b'], 5), { shown: ['a', 'b'], hidden: 0 });
  });

  test('reports how many entries were elided', () => {
    assert.deepEqual(splitForDisplay(['a', 'b', 'c'], 2), { shown: ['a', 'b'], hidden: 1 });
  });

  test('handles a missing list', () => {
    assert.deepEqual(splitForDisplay(null, 3), { shown: [], hidden: 0 });
  });
});
