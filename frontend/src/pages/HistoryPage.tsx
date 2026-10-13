import { Link } from 'react-router-dom';
import { Card, EmptyPanel, ErrorPanel, LoadingPanel, SectionLabel } from '../components/ui';
import { useAsync } from '../hooks/useAsync';
import { analysisService } from '../services/analysisService';
import type { AnalysisSummary } from '../types/api';
import { formatDateTime, formatRelative, scoreBand } from '../utils/format';

/**
 * Analysis history.
 *
 * Stitch ships no dedicated history screen (its top nav links to one), so this
 * reuses the row markup from the dashboard's "Recent Job Analyses" card and
 * makes it full-width and responsive.
 */
export function HistoryPage() {
  const state = useAsync<AnalysisSummary[]>(() => analysisService.list(), []);
  const rows = state.data ?? [];

  return (
    <div className="w-full max-w-7xl mx-auto px-gutter-mobile lg:px-gutter py-space-xl flex flex-col gap-space-lg">
      <div className="flex flex-col gap-space-xs">
        <div className="flex items-center gap-space-xs font-label-badge text-label-badge text-on-surface-variant">
          <span>Analyses</span>
          <span className="material-symbols-outlined text-[14px]">chevron_right</span>
          <span className="text-on-surface font-semibold">Analysis History</span>
        </div>
        <div className="flex flex-col md:flex-row md:items-end justify-between gap-space-md pt-space-xs">
          <div className="space-y-1">
            <h1 className="font-headline-lg-mobile sm:font-headline-lg text-headline-lg-mobile sm:text-headline-lg text-on-surface font-semibold tracking-tight">
              Analysis History
            </h1>
            <p className="font-body-md text-body-md text-on-surface-variant max-w-3xl">
              Every stored ATS analysis, newest first. Opening a row loads the exact saved result from the
              backend.
            </p>
          </div>
          <Link
            to="/analysis/new"
            className="inline-flex items-center gap-space-xs px-space-md py-2 rounded-lg bg-primary-container text-on-primary font-body-md text-body-md font-medium hover:bg-primary transition-colors shadow-sm self-start"
          >
            <span className="material-symbols-outlined text-[18px]">add</span>
            <span>New Analysis</span>
          </Link>
        </div>
      </div>

      <Card className="flex flex-col gap-space-md">
        <div className="flex items-center justify-between">
          <div className="flex flex-col">
            <SectionLabel>Diagnostics Log</SectionLabel>
            <h2 className="font-headline-sm text-headline-sm font-semibold text-on-surface">
              Stored Analyses
            </h2>
          </div>
          {!state.loading && !state.error && (
            <span className="font-label-code text-label-code text-on-surface-variant">
              {rows.length} {rows.length === 1 ? 'record' : 'records'}
            </span>
          )}
        </div>

        {state.loading && <LoadingPanel label="Loading analysis history…" />}

        {!state.loading && state.error && <ErrorPanel message={state.error} onRetry={state.reload} />}

        {!state.loading && !state.error && rows.length === 0 && (
          <EmptyPanel
            icon="history"
            title="No analyses stored yet"
            description="Run your first analysis to build up a history of ATS results."
            action={
              <Link
                to="/analysis/new"
                className="mt-space-xs inline-flex items-center gap-space-xs px-space-md py-2 rounded-lg bg-primary-container text-on-primary font-body-md text-body-md font-medium hover:bg-primary transition-colors"
              >
                <span className="material-symbols-outlined text-[18px]">add</span>
                <span>Start a new analysis</span>
              </Link>
            }
          />
        )}

        {!state.loading && !state.error && rows.length > 0 && (
          <>
            {/* Desktop table */}
            <div className="hidden lg:block overflow-x-auto">
              <table className="w-full border-collapse">
                <thead>
                  <tr className="text-left border-b border-outline-variant/40">
                    <th className="font-label-badge text-label-badge uppercase tracking-wider text-on-surface-variant py-space-sm pr-space-md">
                      Job Title
                    </th>
                    <th className="font-label-badge text-label-badge uppercase tracking-wider text-on-surface-variant py-space-sm pr-space-md">
                      Company
                    </th>
                    <th className="font-label-badge text-label-badge uppercase tracking-wider text-on-surface-variant py-space-sm pr-space-md">
                      Resume
                    </th>
                    <th className="font-label-badge text-label-badge uppercase tracking-wider text-on-surface-variant py-space-sm pr-space-md">
                      ATS Score
                    </th>
                    <th className="font-label-badge text-label-badge uppercase tracking-wider text-on-surface-variant py-space-sm pr-space-md">
                      Date
                    </th>
                    <th className="font-label-badge text-label-badge uppercase tracking-wider text-on-surface-variant py-space-sm" />
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => {
                    const band = scoreBand(row.atsScore);
                    return (
                      <tr
                        key={row.id}
                        className="border-b border-outline-variant/20 last:border-b-0 hover:bg-surface-container-low transition-colors"
                      >
                        <td className="py-space-md pr-space-md">
                          <span className="font-body-md text-body-md font-semibold text-on-surface">
                            {row.jobTitle}
                          </span>
                        </td>
                        <td className="py-space-md pr-space-md">
                          <span className="font-body-sm text-body-sm text-on-surface-variant">
                            {row.companyName ?? '—'}
                          </span>
                        </td>
                        <td className="py-space-md pr-space-md">
                          <span className="font-label-code text-label-code text-on-surface-variant">
                            {row.resumeFileName}
                          </span>
                        </td>
                        <td className="py-space-md pr-space-md">
                          <div className="flex items-center gap-space-xs">
                            <span className="font-headline-sm text-headline-sm font-bold text-on-surface font-label-code">
                              {row.atsScore}
                            </span>
                            <span
                              className={`px-space-xs py-0.5 rounded font-label-badge text-label-badge font-semibold ${band.className}`}
                            >
                              {band.label}
                            </span>
                          </div>
                        </td>
                        <td className="py-space-md pr-space-md">
                          <span className="font-body-sm text-body-sm text-on-surface-variant">
                            {formatDateTime(row.createdAt)}
                          </span>
                        </td>
                        <td className="py-space-md">
                          <Link
                            to={`/results/${row.id}`}
                            className="px-space-md py-1.5 rounded-lg bg-surface-container text-on-surface font-body-sm text-body-sm font-medium hover:bg-primary hover:text-on-primary transition-all"
                          >
                            View analysis
                          </Link>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Mobile / tablet cards */}
            <div className="lg:hidden flex flex-col gap-space-xs">
              {rows.map((row) => {
                const band = scoreBand(row.atsScore);
                return (
                  <div
                    key={row.id}
                    className="p-space-md rounded-xl bg-surface-container-low flex flex-col gap-space-sm"
                  >
                    <div className="flex items-start justify-between gap-space-sm">
                      <div className="flex flex-col min-w-0">
                        <span className="font-body-md text-body-md font-semibold text-on-surface truncate">
                          {row.jobTitle}
                        </span>
                        <span className="font-body-sm text-body-sm text-on-surface-variant truncate">
                          {row.companyName ?? 'No company specified'}
                        </span>
                      </div>
                      <div className="flex items-center gap-space-xs shrink-0">
                        <span className="font-headline-sm text-headline-sm font-bold text-on-surface font-label-code">
                          {row.atsScore}
                        </span>
                        <span
                          className={`px-space-xs py-0.5 rounded font-label-badge text-label-badge font-semibold ${band.className}`}
                        >
                          {band.label}
                        </span>
                      </div>
                    </div>
                    <div className="flex items-center justify-between gap-space-sm flex-wrap">
                      <span className="font-label-code text-label-code text-on-surface-variant truncate">
                        {row.resumeFileName} • {formatRelative(row.createdAt)}
                      </span>
                      <Link
                        to={`/results/${row.id}`}
                        className="px-space-md py-1.5 rounded-lg bg-surface-container text-on-surface font-body-sm text-body-sm font-medium hover:bg-primary hover:text-on-primary transition-all"
                      >
                        View analysis
                      </Link>
                    </div>
                  </div>
                );
              })}
            </div>
          </>
        )}
      </Card>
    </div>
  );
}
