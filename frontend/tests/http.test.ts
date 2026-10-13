import { test, describe } from 'node:test';
import assert from 'node:assert/strict';

import {
  fallbackDownloadName,
  fileNameFromContentDisposition,
  friendlyMessage,
} from '../src/utils/http.ts';

/**
 * Unit tests for the HTTP/error presentation helpers used by the API layer and the
 * PDF report download.
 */

describe('friendlyMessage', () => {
  test('covers the statuses the API actually returns', () => {
    assert.match(friendlyMessage(400), /rejected/i);
    assert.match(friendlyMessage(401), /session/i);
    assert.match(friendlyMessage(404), /could not be found/i);
    assert.match(friendlyMessage(409), /already exists/i);
    assert.match(friendlyMessage(413), /too large/i);
    assert.match(friendlyMessage(422), /could not be read/i);
    assert.match(friendlyMessage(500), /problem/i);
    assert.match(friendlyMessage(502), /upstream/i);
    assert.match(friendlyMessage(503), /not available/i);
    assert.match(friendlyMessage(504), /timed out/i);
  });

  test('falls back to a generic sentence for an unmapped status', () => {
    assert.match(friendlyMessage(418), /Something went wrong/i);
  });

  test('never leaks Java exception or class names', () => {
    for (const status of [400, 401, 403, 404, 409, 413, 422, 500, 502, 503, 504]) {
      const message = friendlyMessage(status);
      assert.doesNotMatch(message, /Exception|java\.|org\.springframework|null/i);
    }
  });
});

describe('fileNameFromContentDisposition', () => {
  test('reads a quoted plain filename', () => {
    assert.equal(
      fileNameFromContentDisposition('attachment; filename="smartresume-engineer-12.pdf"'),
      'smartresume-engineer-12.pdf',
    );
  });

  test('reads an unquoted plain filename', () => {
    assert.equal(
      fileNameFromContentDisposition('attachment; filename=report.pdf'),
      'report.pdf',
    );
  });

  test('prefers the RFC 5987 encoded form and decodes it', () => {
    assert.equal(
      fileNameFromContentDisposition(
        "attachment; filename=\"fallback.pdf\"; filename*=UTF-8''r%C3%A9sum%C3%A9-12.pdf",
      ),
      'résumé-12.pdf',
    );
  });

  test('returns null for missing, empty or unparseable headers', () => {
    assert.equal(fileNameFromContentDisposition(null), null);
    assert.equal(fileNameFromContentDisposition(undefined), null);
    assert.equal(fileNameFromContentDisposition(''), null);
    assert.equal(fileNameFromContentDisposition('attachment'), null);
  });

  test('survives malformed percent-encoding in the extended form', () => {
    assert.equal(
      fileNameFromContentDisposition("attachment; filename*=UTF-8''%E0%A4%A; filename=\"ok.pdf\""),
      'ok.pdf',
    );
  });

  test('does not match a filename appearing inside other text', () => {
    // A header without a filename parameter must not yield garbage.
    assert.equal(fileNameFromContentDisposition('inline; size=1234'), null);
  });
});

describe('fallbackDownloadName', () => {
  test('builds a name from the analysis id', () => {
    assert.equal(fallbackDownloadName(7), 'smartresume-analysis-7.pdf');
  });
});
