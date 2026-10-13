/** Small presentation helpers. No business logic, no data invention. */

export function formatBytes(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined) return '—';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** LocalDateTime from the backend is an ISO string without a timezone. */
export function parseBackendDate(value: string | null | undefined): Date | null {
  if (!value) return null;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

export function formatDate(value: string | null | undefined): string {
  const date = parseBackendDate(value);
  if (!date) return '—';
  return date.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: '2-digit' });
}

export function formatDateTime(value: string | null | undefined): string {
  const date = parseBackendDate(value);
  if (!date) return '—';
  return date.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** "Today" / "2 days ago" / fallback to an absolute date. */
export function formatRelative(value: string | null | undefined): string {
  const date = parseBackendDate(value);
  if (!date) return '—';
  const diffMs = Date.now() - date.getTime();
  const days = Math.floor(diffMs / 86_400_000);
  if (days <= 0) return 'Today';
  if (days === 1) return 'Yesterday';
  if (days < 7) return `${days} days ago`;
  return formatDate(value);
}

export interface ScoreBand {
  label: string;
  /** Tailwind classes for a badge built from the Stitch token palette. */
  className: string;
}

/** Qualitative band for a 0–100 ATS score. Thresholds are presentation-only. */
export function scoreBand(score: number): ScoreBand {
  if (score >= 85) {
    return { label: 'Exceptional', className: 'bg-secondary-container text-on-secondary-container' };
  }
  if (score >= 75) {
    return { label: 'Strong', className: 'bg-secondary-container text-on-secondary-container' };
  }
  if (score >= 60) {
    return { label: 'Fair', className: 'bg-surface-container-high text-on-surface' };
  }
  return { label: 'Needs Work', className: 'bg-tertiary-fixed/40 text-tertiary' };
}

export function uploadStatusLabel(status: string): string {
  switch (status) {
    case 'PARSED':
      return 'Parsed successfully';
    case 'PROCESSING':
      return 'Processing';
    case 'UPLOADED':
      return 'Uploaded';
    case 'FAILED':
      return 'Parsing failed';
    default:
      return status;
  }
}

/** Text for a 0–100 metric card badge. */
export function levelLabel(value: number): string {
  if (value >= 85) return 'High';
  if (value >= 70) return 'Optimal';
  if (value >= 50) return 'Moderate';
  return 'Low';
}
