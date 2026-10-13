import { test, describe } from 'node:test';
import assert from 'node:assert/strict';

import {
  formatBytes,
  levelLabel,
  parseBackendDate,
  scoreBand,
  uploadStatusLabel,
} from '../src/utils/format.ts';

/**
 * Unit tests for the presentation helpers shared by the dashboard, history and
 * results pages. Only locale-independent outputs are asserted.
 */

describe('scoreBand', () => {
  test('bands a score at each documented threshold', () => {
    assert.equal(scoreBand(100).label, 'Exceptional');
    assert.equal(scoreBand(85).label, 'Exceptional');
    assert.equal(scoreBand(84).label, 'Strong');
    assert.equal(scoreBand(75).label, 'Strong');
    assert.equal(scoreBand(74).label, 'Fair');
    assert.equal(scoreBand(60).label, 'Fair');
    assert.equal(scoreBand(59).label, 'Needs Work');
    assert.equal(scoreBand(0).label, 'Needs Work');
  });

  test('every band carries a Tailwind class rather than an inline colour', () => {
    for (const score of [0, 50, 70, 80, 90]) {
      assert.match(scoreBand(score).className, /bg-/);
    }
  });
});

describe('levelLabel', () => {
  test('bands a metric value at each documented threshold', () => {
    assert.equal(levelLabel(90), 'High');
    assert.equal(levelLabel(85), 'High');
    assert.equal(levelLabel(84), 'Optimal');
    assert.equal(levelLabel(70), 'Optimal');
    assert.equal(levelLabel(69), 'Moderate');
    assert.equal(levelLabel(50), 'Moderate');
    assert.equal(levelLabel(49), 'Low');
  });
});

describe('formatBytes', () => {
  test('formats each unit', () => {
    assert.equal(formatBytes(512), '512 B');
    assert.equal(formatBytes(2048), '2.0 KB');
    assert.equal(formatBytes(5 * 1024 * 1024), '5.0 MB');
  });

  test('uses a dash for unknown values instead of "NaN"', () => {
    assert.equal(formatBytes(null), '—');
    assert.equal(formatBytes(undefined), '—');
  });
});

describe('uploadStatusLabel', () => {
  test('maps every backend upload status to readable text', () => {
    assert.equal(uploadStatusLabel('PARSED'), 'Parsed successfully');
    assert.equal(uploadStatusLabel('FAILED'), 'Parsing failed');
    assert.equal(uploadStatusLabel('UPLOADED'), 'Uploaded');
    assert.equal(uploadStatusLabel('PROCESSING'), 'Processing');
  });

  test('passes an unknown status through rather than hiding it', () => {
    assert.equal(uploadStatusLabel('SOMETHING_NEW'), 'SOMETHING_NEW');
  });
});

describe('parseBackendDate', () => {
  test('parses the backend ISO shape', () => {
    const parsed = parseBackendDate('2026-10-11T15:30:00');
    assert.ok(parsed instanceof Date);
    assert.equal(parsed?.getFullYear(), 2026);
  });

  test('returns null for missing or invalid input instead of an Invalid Date', () => {
    assert.equal(parseBackendDate(null), null);
    assert.equal(parseBackendDate(undefined), null);
    assert.equal(parseBackendDate(''), null);
    assert.equal(parseBackendDate('not-a-date'), null);
  });
});
