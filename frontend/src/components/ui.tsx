import type { ReactNode } from 'react';

/** Clamp helper for every visual that maps a value onto a 0–100 geometry. */
function clampPercent(value: number): number {
  if (Number.isNaN(value)) return 0;
  return Math.min(100, Math.max(0, value));
}

// ---------------------------------------------------------------------------
// Badges (Stitch "Status Badges & Keyword Tags")
// ---------------------------------------------------------------------------

export type BadgeTone = 'success' | 'danger' | 'warning' | 'neutral' | 'info' | 'solid';

const BADGE_TONES: Record<BadgeTone, string> = {
  success: 'bg-secondary-container text-on-secondary-container',
  danger: 'bg-tertiary-fixed text-tertiary',
  warning: 'bg-tertiary-fixed/40 text-tertiary',
  neutral: 'bg-surface-container text-on-surface-variant',
  info: 'bg-primary-fixed text-on-primary-fixed-variant',
  solid: 'bg-tertiary-container text-on-tertiary',
};

export function Badge({
  children,
  tone = 'neutral',
  className = '',
}: {
  children: ReactNode;
  tone?: BadgeTone;
  className?: string;
}) {
  return (
    <span
      className={`inline-flex items-center gap-1 px-space-xs py-0.5 rounded-DEFAULT font-label-badge text-label-badge font-medium ${BADGE_TONES[tone]} ${className}`}
    >
      {children}
    </span>
  );
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

export function Card({
  children,
  className = '',
  padded = true,
  id,
}: {
  children: ReactNode;
  className?: string;
  padded?: boolean;
  id?: string;
}) {
  return (
    <div
      id={id}
      className={`rounded-xl bg-surface-container-lowest shadow-sm ${padded ? 'p-space-lg' : ''} ${className}`}
    >
      {children}
    </div>
  );
}

export function SectionLabel({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <span
      className={`font-label-badge text-label-badge uppercase tracking-wider text-on-surface-variant ${className}`}
    >
      {children}
    </span>
  );
}

// ---------------------------------------------------------------------------
// Progress bar
// ---------------------------------------------------------------------------

export function ProgressBar({
  value,
  barClassName = 'bg-secondary',
  heightClassName = 'h-2',
}: {
  value: number;
  barClassName?: string;
  heightClassName?: string;
}) {
  return (
    <div className={`w-full ${heightClassName} rounded-full bg-surface-container-high overflow-hidden`}>
      <div
        className={`h-full rounded-full transition-[width] duration-700 ease-out ${barClassName}`}
        style={{ width: `${clampPercent(value)}%` }}
      />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Concentric score dial (Stitch "ATS Score Dial")
// ---------------------------------------------------------------------------

export function ScoreDial({
  value,
  size = 'md',
  innerValue = null,
  children,
}: {
  value: number;
  size?: 'md' | 'lg';
  /** Optional second ring, used for the skill-alignment delta. */
  innerValue?: number | null;
  children?: ReactNode;
}) {
  const isLarge = size === 'lg';
  const box = isLarge ? 120 : 100;
  const center = box / 2;
  const radius = isLarge ? 50 : 40;
  const strokeWidth = isLarge ? 10 : 8;
  const circumference = 2 * Math.PI * radius;
  const dashOffset = circumference * (1 - clampPercent(value) / 100);

  const innerRadius = 33;
  const innerCircumference = 2 * Math.PI * innerRadius;
  const innerDashOffset =
    innerValue === null ? 0 : innerCircumference * (1 - clampPercent(innerValue) / 100);

  const dimension = isLarge ? 'w-36 h-36' : 'w-28 h-28';

  return (
    // shrink-0 matters: without it a long paragraph in the same flex row
    // compresses the ring and the centred score overflows it.
    <div className={`relative ${dimension} shrink-0 flex items-center justify-center`}>
      <svg className="w-full h-full -rotate-90" viewBox={`0 0 ${box} ${box}`}>
        <circle
          className="text-surface-container"
          cx={center}
          cy={center}
          fill="none"
          r={radius}
          stroke="currentColor"
          strokeWidth={strokeWidth}
        />
        <circle
          className="text-primary-container transition-all duration-1000 ease-out"
          cx={center}
          cy={center}
          fill="none"
          r={radius}
          stroke="currentColor"
          strokeDasharray={circumference.toFixed(1)}
          strokeDashoffset={dashOffset.toFixed(1)}
          strokeLinecap="round"
          strokeWidth={strokeWidth}
        />
        {innerValue !== null && (
          <circle
            className="text-secondary transition-all duration-1000 ease-out"
            cx={center}
            cy={center}
            fill="none"
            r={innerRadius}
            stroke="currentColor"
            strokeDasharray={innerCircumference.toFixed(1)}
            strokeDashoffset={innerDashOffset.toFixed(1)}
            strokeLinecap="round"
            strokeWidth={5}
          />
        )}
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center text-center">{children}</div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Async state panels — loading / error / empty
// ---------------------------------------------------------------------------

export function LoadingPanel({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex flex-col items-center justify-center gap-space-sm py-space-xl text-on-surface-variant">
      <span className="material-symbols-outlined text-[32px] text-primary animate-spin">refresh</span>
      <span className="font-body-md text-body-md font-medium">{label}</span>
      <span className="font-label-code text-label-code">Waiting for the backend response</span>
    </div>
  );
}

export function ErrorPanel({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="flex flex-col gap-space-sm p-space-md rounded-xl bg-tertiary-fixed/30">
      <div className="flex items-start gap-space-sm">
        <span className="material-symbols-outlined text-tertiary text-[20px] shrink-0 mt-0.5">
          report_problem
        </span>
        <div className="flex flex-col">
          <span className="font-label-badge text-label-badge uppercase font-semibold text-tertiary">
            Request failed
          </span>
          <p className="font-body-md text-body-md text-on-surface">{message}</p>
        </div>
      </div>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="self-start inline-flex items-center gap-space-xs px-space-md py-1.5 rounded-lg bg-surface-container text-on-surface font-body-sm text-body-sm font-medium hover:bg-surface-container-high transition-colors"
        >
          <span className="material-symbols-outlined text-[16px]">refresh</span>
          Try again
        </button>
      )}
    </div>
  );
}

export function EmptyPanel({
  icon = 'inbox',
  title,
  description,
  action,
}: {
  icon?: string;
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-space-sm py-space-xl text-center">
      <span className="material-symbols-outlined text-[32px] text-on-surface-variant">{icon}</span>
      <span className="font-headline-sm text-headline-sm text-on-surface">{title}</span>
      <p className="font-body-md text-body-md text-on-surface-variant max-w-md">{description}</p>
      {action}
    </div>
  );
}
