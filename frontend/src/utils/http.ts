/**
 * HTTP presentation helpers.
 *
 * Deliberately free of any Vite-specific import (no `import.meta.env`) so this module
 * can be unit-tested in plain Node as well as bundled by Vite.
 */

/**
 * Maps a status code to a user-friendly sentence.
 *
 * Raw Spring/Java exception text is never surfaced here; the caller appends the
 * backend's own ErrorResponse.message, which GlobalExceptionHandler already writes
 * for humans.
 */
export function friendlyMessage(status: number): string {
  switch (status) {
    case 400:
      return 'The request was rejected. Please check the values you entered.';
    case 401:
      return 'Your session is not valid. Please sign in again.';
    case 403:
      return 'You are not allowed to perform this action.';
    case 404:
      return 'The requested item could not be found.';
    case 409:
      return 'That already exists — most likely the email address is already registered.';
    case 413:
      return 'The file is too large. The maximum size is 10 MB.';
    case 422:
      return 'The uploaded document could not be read. It may be image-based, empty or corrupted.';
    case 500:
      return 'The server ran into a problem while handling the request.';
    case 502:
      return 'An upstream service returned an error. Please try again shortly.';
    case 503:
      return 'This feature is not available on the server right now.';
    case 504:
      return 'The request timed out before the server responded.';
    default:
      return 'Something went wrong while contacting the server.';
  }
}

/**
 * Extract a file name from a Content-Disposition header value.
 *
 * Prefers RFC 5987 `filename*=UTF-8''...` when present, then falls back to the plain
 * `filename=` parameter. Returns null when the header is absent or unparseable, in
 * which case the caller supplies its own name.
 */
export function fileNameFromContentDisposition(value: string | null | undefined): string | null {
  if (!value) return null;

  const extended = /filename\*=UTF-8''([^;]+)/i.exec(value);
  if (extended && extended[1]) {
    try {
      const decoded = decodeURIComponent(extended[1].trim());
      if (decoded) return decoded;
    } catch {
      // Malformed percent-encoding: fall through to the plain parameter.
    }
  }

  const plain = /filename="?([^";]+)"?/i.exec(value);
  if (plain && plain[1]) {
    const trimmed = plain[1].trim();
    if (trimmed) return trimmed;
  }

  return null;
}

/** A last-resort download name when the server did not send one. */
export function fallbackDownloadName(analysisId: number): string {
  return `smartresume-analysis-${analysisId}.pdf`;
}
