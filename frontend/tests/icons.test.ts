import { test, describe } from 'node:test';
import assert from 'node:assert/strict';

import {
  ICON_FONT_FAMILY,
  ICON_STATE_ATTRIBUTE,
  applyIconFontState,
  resolveIconFontState,
} from '../src/utils/icons.ts';

/**
 * Unit tests for the offline icon-font detection (stage G).
 *
 * The important property is that detection fails OPEN: anything unexpected must leave
 * the icons visible, because wrongly hiding every icon is worse than a placeholder.
 */

function probeReturning(result: boolean) {
  const asked: string[] = [];
  return {
    asked,
    probe: {
      check(font: string) {
        asked.push(font);
        return result;
      },
    },
  };
}

describe('resolveIconFontState', () => {
  test('reports available when the font can be measured', () => {
    const { probe } = probeReturning(true);
    assert.equal(resolveIconFontState(probe), 'available');
  });

  test('reports unavailable when the font cannot be measured', () => {
    const { probe } = probeReturning(false);
    assert.equal(resolveIconFontState(probe), 'unavailable');
  });

  test('asks about the real family name', () => {
    const { asked, probe } = probeReturning(true);
    resolveIconFontState(probe);
    assert.ok(asked.some((spec) => spec.includes(ICON_FONT_FAMILY)));
  });

  test('fails open when the Font Loading API is missing', () => {
    assert.equal(resolveIconFontState(null), 'available');
    assert.equal(resolveIconFontState(undefined), 'available');
    assert.equal(resolveIconFontState({} as never), 'available');
  });

  test('fails open when check() throws', () => {
    const probe = {
      check() {
        throw new Error('unsupported');
      },
    };
    assert.equal(resolveIconFontState(probe), 'available');
  });

  test('accepts an explicit family name', () => {
    const { asked, probe } = probeReturning(false);
    assert.equal(resolveIconFontState(probe, 'Some Other Icons'), 'unavailable');
    assert.ok(asked.some((spec) => spec.includes('Some Other Icons')));
  });
});

describe('applyIconFontState', () => {
  test('writes the state onto the target element', () => {
    const written: Record<string, string> = {};
    const target = {
      setAttribute(name: string, value: string) {
        written[name] = value;
      },
    };

    applyIconFontState('unavailable', target);
    assert.equal(written[ICON_STATE_ATTRIBUTE], 'unavailable');

    applyIconFontState('available', target);
    assert.equal(written[ICON_STATE_ATTRIBUTE], 'available');
  });

  test('returns the state it applied, so callers can log or assert it', () => {
    const target = { setAttribute() {} };
    assert.equal(applyIconFontState('available', target), 'available');
  });
});
