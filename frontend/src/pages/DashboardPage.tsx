import { Link } from 'react-router-dom';
import type { ReactNode } from 'react';
import {
  Badge,
  Card,
  EmptyPanel,
  ErrorPanel,
  LoadingPanel,
  ProgressBar,
  ScoreDial,
  SectionLabel,
} from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { useAsync } from '../hooks/useAsync';
import { analysisService } from '../services/analysisService';
import { resumeService } from '../services/resumeService';
import type { Analysis, AnalysisSummary, Resume } from '../types/api';
import { formatDate, formatRelative, scoreBand } from '../utils/format';

interface DashboardData {
  resumes: Resume[];
  analyses: AnalysisSummary[];
  /** Full payloads for the most recent analyses, used for the missing-skill matrix. */
  recentDetails: Analysis[];
}

/** How many recent analyses are loaded in full for the deficit matrix. */
const RECENT_DETAIL_LIMIT = 5;

// ---------------------------------------------------------------------------
// Small presentational helpers
// ---------------------------------------------------------------------------

function KpiCard({
  label,
  icon,
  iconTone,
  value,
  unit,
  badge,
  caption,
}: {
  label: string;
  icon: string;
  iconTone: string;
  value: string;
  unit?: string;
  badge?: ReactNode;
  caption: string;
}) {
  return (
    <div className="p-space-md rounded-xl bg-surface-container-lowest shadow-sm flex flex-col justify-between gap-space-md group hover:bg-surface-container-low transition-all">
      <div className="flex items-center justify-between">
        <SectionLabel>{label}</SectionLabel>
        <span className={`p-2 rounded-lg flex items-center justify-center ${iconTone}`}>
          <span className="material-symbols-outlined text-[20px]">{icon}</span>
        </span>
      </div>
      <div className="flex flex-col gap-1">
        <div className="flex items-baseline gap-space-sm flex-wrap">
          <span className="font-display text-headline-lg font-bold text-on-surface tracking-tight">{value}</span>
          {unit && <span className="font-body-md text-body-md text-on-surface-variant">{unit}</span>}
          {badge}
        </div>
        <span className="font-body-sm text-body-sm text-on-surface-variant">{caption}</span>
      </div>
    </div>
  );
}

function MetricBar({
  label,
  value,
  dotClass,
  barClass,
}: {
  label: string;
  value: number;
  dotClass: string;
  barClass: string;
}) {
  return (
    <div className="flex flex-col gap-1">
      <div className="flex justify-between items-center font-body-sm text-body-sm font-medium">
        <span className="text-on-surface flex items-center gap-1.5">
          <span className={`w-2 h-2 rounded-full ${dotClass}`} />
          {label}
        </span>
        <span className="text-on-surface font-label-code text-label-code">{value}%</span>
      </div>
      <ProgressBar value={value} barClassName={barClass} />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Page
// ---------------------------------------------------------------------------

export function DashboardPage() {
  const { user } = useAuth();

  const state = useAsync<DashboardData>(async () => {
    const [resumes, analyses] = await Promise.all([resumeService.list(), analysisService.list()]);
    const recentDetails = await Promise.all(
      analyses.slice(0, RECENT_DETAIL_LIMIT).map((summary) => analysisService.getById(summary.id)),
    );
    return { resumes, analyses, recentDetails };
  }, []);

  if (state.loading) {
    return (
      <div className="w-full px-gutter-mobile lg:px-gutter py-space-lg max-w-[1600px] mx-auto">
        <LoadingPanel label="Loading your dashboard…" />
      </div>
    );
  }

  if (state.error || !state.data) {
    return (
      <div className="w-full px-gutter-mobile lg:px-gutter py-space-lg max-w-[1600px] mx-auto">
        <ErrorPanel message={state.error ?? 'No data returned by the backend.'} onRetry={state.reload} />
      </div>
    );
  }

  const { resumes, analyses, recentDetails } = state.data;

  const parsedResumes = resumes.filter((resume) => resume.uploadStatus === 'PARSED');
  const averageScore =
    analyses.length > 0 ? analyses.reduce((sum, item) => sum + item.atsScore, 0) / analyses.length : null;
  const best = analyses.reduce<AnalysisSummary | null>(
    (max, item) => (max === null || item.atsScore > max.atsScore ? item : max),
    null,
  );

  // Union of missing skills across the most recent analyses, with real counts.
  const missingSkillCounts = new Map<string, number>();
  for (const detail of recentDetails) {
    for (const skill of detail.missingSkills ?? []) {
      missingSkillCounts.set(skill, (missingSkillCounts.get(skill) ?? 0) + 1);
    }
  }
  const topMissingSkills = [...missingSkillCounts.entries()]
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
    .slice(0, 4);

  const latest = recentDetails[0] ?? null;
  const recentRows = analyses.slice(0, 4);

  return (
    <div className="w-full px-gutter-mobile lg:px-gutter py-space-lg flex flex-col gap-space-lg max-w-[1600px] mx-auto">
      {/* Top greeting */}
      <section className="flex flex-col md:flex-row md:items-end justify-between gap-space-md pb-space-sm">
        <div className="flex flex-col gap-space-xs">
          <div className="flex items-center gap-space-sm flex-wrap">
            <span className="px-space-xs py-0.5 rounded bg-surface-container text-on-surface-variant font-label-badge text-label-badge uppercase tracking-wider">
              Candidate Telemetry
            </span>
            <span className="text-outline-variant font-label-code text-label-code">•</span>
            <span className="text-secondary font-label-code text-label-code flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-secondary animate-pulse" />
              {analyses.length} {analyses.length === 1 ? 'analysis' : 'analyses'} stored
            </span>
          </div>
          <h1 className="font-headline-lg-mobile sm:font-headline-lg text-headline-lg-mobile sm:text-headline-lg text-on-surface tracking-tight">
            Welcome back, {user?.name ?? 'there'}
          </h1>
          <p className="font-body-md text-body-md text-on-surface-variant max-w-2xl">
            An overview of your resume ATS results and job match readiness across the job descriptions you
            are targeting.
          </p>
        </div>
        <div className="flex items-center gap-space-sm shrink-0">
          <Link
            to="/analysis/new"
            className="inline-flex items-center gap-space-xs px-space-md py-2 rounded-lg bg-surface-container-lowest text-on-surface hover:bg-surface-container-high transition-colors shadow-sm font-body-md text-body-md font-medium"
          >
            <span className="material-symbols-outlined text-[18px]">upload_file</span>
            <span>Upload New Version</span>
          </Link>
          <Link
            to="/analysis/new"
            className="inline-flex items-center gap-space-xs px-space-md py-2 rounded-lg bg-primary-container text-on-primary hover:bg-primary transition-all shadow-sm font-body-md text-body-md font-medium"
          >
            <span className="material-symbols-outlined text-[18px]">add</span>
            <span>New Resume Analysis</span>
          </Link>
        </div>
      </section>

      {/* KPI cards — every value computed from GET /api/resumes + GET /api/analyses */}
      <section className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-space-md">
        <KpiCard
          label="Resumes Analyzed"
          icon="description"
          iconTone="bg-surface-container text-primary"
          value={String(resumes.length)}
          badge={
            parsedResumes.length > 0 ? (
              <span className="font-label-code text-label-code text-secondary font-semibold flex items-center">
                <span className="material-symbols-outlined text-[14px]">check_circle</span>
                {parsedResumes.length} parsed
              </span>
            ) : undefined
          }
          caption="Resumes stored in the parsing pipeline"
        />

        <KpiCard
          label="Average ATS Score"
          icon="analytics"
          iconTone="bg-secondary-container/40 text-secondary"
          value={averageScore === null ? '—' : averageScore.toFixed(1)}
          unit={averageScore === null ? undefined : '/ 100'}
          caption={
            averageScore === null
              ? 'No analyses yet — run your first analysis'
              : `Mean of ${analyses.length} stored ${analyses.length === 1 ? 'analysis' : 'analyses'}`
          }
        />

        <KpiCard
          label="Highest Match Score"
          icon="military_tech"
          iconTone="bg-surface-container text-surface-tint"
          value={best === null ? '—' : String(best.atsScore)}
          unit={best === null ? undefined : '/ 100'}
          caption={
            best === null
              ? 'No analyses yet'
              : `${best.jobTitle}${best.companyName ? ` @ ${best.companyName}` : ''}`
          }
        />

        <KpiCard
          label="Distinct Missing Skills"
          icon="priority_high"
          iconTone="bg-surface-container text-tertiary"
          value={String(missingSkillCounts.size)}
          caption={
            recentDetails.length === 0
              ? 'No analyses yet'
              : `Across your ${recentDetails.length} most recent ${recentDetails.length === 1 ? 'analysis' : 'analyses'}`
          }
        />
      </section>

      {/* Workspace grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-lg items-start">
        {/* LEFT */}
        <div className="lg:col-span-8 flex flex-col gap-space-lg">
          {latest === null ? (
            <Card>
              <EmptyPanel
                icon="analytics"
                title="No analysis yet"
                description="Upload a resume and a job description to generate your first ATS diagnostic. The results will appear here."
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
            </Card>
          ) : (
            <div className="rounded-xl bg-surface-container-lowest shadow-sm p-space-lg flex flex-col gap-space-md relative overflow-hidden">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-space-sm pb-space-sm bg-surface-container-low/50 -mx-space-lg -mt-space-lg p-space-lg">
                <div className="flex items-center gap-space-sm">
                  <span className="material-symbols-outlined text-primary text-[24px]">verified</span>
                  <div>
                    <SectionLabel>Latest Analysis Highlights</SectionLabel>
                    <h2 className="font-headline-sm text-headline-sm font-semibold text-on-surface">
                      {latest.jobTitle}
                    </h2>
                  </div>
                </div>
                <div className="flex items-center gap-2 flex-wrap">
                  {latest.companyName && (
                    <span className="px-space-sm py-1 rounded bg-surface-container text-on-surface-variant font-label-badge text-label-badge font-medium">
                      {latest.companyName}
                    </span>
                  )}
                  <span className="px-space-sm py-1 rounded bg-secondary-container text-on-secondary-container font-label-badge text-label-badge font-semibold">
                    {formatRelative(latest.createdAt)}
                  </span>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-12 gap-space-md items-center pt-space-xs">
                <div className="md:col-span-4 flex flex-col items-center justify-center p-space-md bg-surface-container-low rounded-xl text-center">
                  <ScoreDial value={latest.atsScore}>
                    <div className="flex items-baseline">
                      <span className="font-headline-lg text-headline-lg font-bold text-on-surface">
                        {latest.atsScore}
                      </span>
                      <span className="font-label-badge text-label-badge text-on-surface-variant">/ 100</span>
                    </div>
                  </ScoreDial>
                  <span className="font-label-badge text-label-badge uppercase tracking-wider text-secondary font-bold mt-2">
                    {scoreBand(latest.atsScore).label} Compatibility
                  </span>
                </div>

                <div className="md:col-span-8 flex flex-col gap-space-sm">
                  <MetricBar
                    label="Skills Index"
                    value={latest.scoreBreakdown.skillMatch ?? 0}
                    dotClass="bg-primary"
                    barClass="bg-primary-container"
                  />
                  <MetricBar
                    label="Keywords Matching"
                    value={latest.scoreBreakdown.keywordMatch ?? 0}
                    dotClass="bg-secondary"
                    barClass="bg-secondary"
                  />
                  <MetricBar
                    label="Document Structure"
                    value={latest.scoreBreakdown.structure ?? 0}
                    dotClass="bg-surface-tint"
                    barClass="bg-surface-tint"
                  />
                </div>
              </div>

              {(latest.missingSkills ?? []).length > 0 && (
                <div className="p-space-md rounded-xl bg-tertiary-fixed/30 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-space-sm">
                  <div className="flex items-start gap-space-sm">
                    <span className="material-symbols-outlined text-tertiary text-[20px] shrink-0 mt-0.5">
                      report_problem
                    </span>
                    <div className="flex flex-col">
                      <span className="font-label-badge text-label-badge uppercase font-semibold text-tertiary">
                        Top Priority Optimization
                      </span>
                      <p className="font-body-md text-body-md text-on-surface font-medium">
                        Missing skills flagged for this role: {(latest.missingSkills ?? []).join(', ')}.
                      </p>
                    </div>
                  </div>
                  <Link
                    to={`/results/${latest.id}`}
                    className="inline-flex items-center gap-1 text-primary font-body-md text-body-md font-semibold shrink-0 hover:text-surface-tint transition-colors"
                  >
                    <span>View Full Report</span>
                    <span className="material-symbols-outlined text-[18px]">arrow_forward</span>
                  </Link>
                </div>
              )}
            </div>
          )}

          {/* Recent analyses */}
          <Card className="flex flex-col gap-space-md">
            <div className="flex items-center justify-between">
              <div className="flex flex-col">
                <SectionLabel>Diagnostics Log</SectionLabel>
                <h2 className="font-headline-sm text-headline-sm font-semibold text-on-surface">
                  Recent Job Analyses
                </h2>
              </div>
              <span className="font-label-code text-label-code text-on-surface-variant">
                {recentRows.length === 0 ? 'No analyses yet' : `Showing ${recentRows.length} of ${analyses.length}`}
              </span>
            </div>

            {recentRows.length === 0 ? (
              <EmptyPanel
                icon="history"
                title="Nothing analysed yet"
                description="Your completed analyses will be listed here with their ATS score and job target."
              />
            ) : (
              <div className="flex flex-col gap-space-xs">
                {recentRows.map((row) => {
                  const band = scoreBand(row.atsScore);
                  return (
                    <div
                      key={row.id}
                      className="p-space-md rounded-xl bg-surface-container-low hover:bg-surface-container-high/60 transition-colors flex flex-col sm:flex-row sm:items-center justify-between gap-space-sm"
                    >
                      <div className="flex items-center gap-space-md min-w-0">
                        <div className="w-10 h-10 rounded-lg bg-surface-container flex items-center justify-center shrink-0 text-primary">
                          <span className="material-symbols-outlined text-[20px]">layers</span>
                        </div>
                        <div className="flex flex-col min-w-0">
                          <span className="font-body-md text-body-md font-semibold text-on-surface truncate">
                            {row.jobTitle}
                          </span>
                          <span className="font-body-sm text-body-sm text-on-surface-variant flex items-center gap-1.5 flex-wrap">
                            <span>{row.companyName ?? 'No company specified'}</span>
                            <span>•</span>
                            <span>{formatDate(row.createdAt)}</span>
                            <span>•</span>
                            <span className="truncate">{row.resumeFileName}</span>
                          </span>
                        </div>
                      </div>
                      <div className="flex items-center justify-between sm:justify-end gap-space-md shrink-0">
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
                        <Link
                          to={`/results/${row.id}`}
                          className="px-space-md py-1.5 rounded-lg bg-surface-container text-on-surface font-body-sm text-body-sm font-medium hover:bg-primary hover:text-on-primary transition-all"
                        >
                          View
                        </Link>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </Card>
        </div>

        {/* RIGHT */}
        <div className="lg:col-span-4 flex flex-col gap-space-lg">
          <Card className="flex flex-col gap-space-md">
            <div className="flex items-center justify-between">
              <div className="flex flex-col">
                <span className="font-label-badge text-label-badge uppercase tracking-wider text-tertiary font-bold">
                  Deficit Matrix
                </span>
                <h3 className="font-headline-sm text-headline-sm font-semibold text-on-surface">
                  Common Missing Skills
                </h3>
              </div>
              <span className="material-symbols-outlined text-tertiary text-[20px]">warning</span>
            </div>
            <p className="font-body-sm text-body-sm text-on-surface-variant">
              Skills the job descriptions asked for that were not found in your resume, counted across your
              most recent analyses.
            </p>

            {topMissingSkills.length === 0 ? (
              <p className="font-body-sm text-body-sm text-on-surface-variant p-space-sm rounded-lg bg-surface-container-low">
                {recentDetails.length === 0
                  ? 'No analyses yet — run an analysis to populate this list.'
                  : 'No missing skills were flagged in your recent analyses.'}
              </p>
            ) : (
              <div className="flex flex-col gap-space-xs">
                {topMissingSkills.map(([skill, count]) => (
                  <div
                    key={skill}
                    className="flex items-center justify-between p-space-sm rounded-lg bg-surface-container-low"
                  >
                    <div className="flex items-center gap-space-xs min-w-0">
                      <span className="font-label-code text-label-code font-bold text-on-surface truncate">
                        {skill}
                      </span>
                      <Badge tone="danger">Missing</Badge>
                    </div>
                    <span className="font-label-badge text-label-badge text-on-surface-variant shrink-0">
                      {count} of {recentDetails.length}
                    </span>
                  </div>
                ))}
              </div>
            )}

            <Link
              to="/analysis/history"
              className="w-full py-2 px-space-md rounded-lg bg-surface-container text-primary font-body-sm text-body-sm font-semibold flex items-center justify-center gap-1.5 hover:bg-surface-container-high transition-colors"
            >
              <span className="material-symbols-outlined text-[16px]">history</span>
              <span>Open Analysis History</span>
            </Link>
          </Card>

          <Card className="flex flex-col gap-space-md">
            <div className="flex items-center justify-between">
              <div className="flex flex-col">
                <span className="font-label-badge text-label-badge uppercase tracking-wider text-secondary font-bold">
                  Reading The Results
                </span>
                <h3 className="font-headline-sm text-headline-sm font-semibold text-on-surface">
                  How Scoring Works
                </h3>
              </div>
              <span className="material-symbols-outlined text-secondary text-[20px]">lightbulb</span>
            </div>
            <div className="flex flex-col gap-space-sm">
              <div className="p-space-sm rounded-lg bg-surface-container-low flex items-start gap-space-sm">
                <span className="material-symbols-outlined text-primary text-[18px] mt-0.5 shrink-0">speed</span>
                <div className="flex flex-col">
                  <span className="font-body-md text-body-md font-semibold text-on-surface">
                    Weighted Deterministic Score
                  </span>
                  <p className="font-body-sm text-body-sm text-on-surface-variant mt-0.5">
                    The overall score is a fixed weighted blend of skill match (40%), keyword match (25%),
                    relevance (15%), structure (12%) and formatting (8%). It is not an industry ATS benchmark.
                  </p>
                </div>
              </div>
              <div className="p-space-sm rounded-lg bg-surface-container-low flex items-start gap-space-sm">
                <span className="material-symbols-outlined text-secondary text-[18px] mt-0.5 shrink-0">
                  manage_search
                </span>
                <div className="flex flex-col">
                  <span className="font-body-md text-body-md font-semibold text-on-surface">
                    Where Missing Skills Come From
                  </span>
                  <p className="font-body-sm text-body-sm text-on-surface-variant mt-0.5">
                    Skills are matched with a curated dictionary against the job description you supplied.
                    Anything the job description asks for that is not found in your resume text is reported as
                    missing.
                  </p>
                </div>
              </div>
            </div>
            <div className="p-space-sm rounded-lg bg-surface-container-low flex flex-col gap-1.5">
              <span className="font-label-badge text-label-badge uppercase tracking-wider text-on-surface-variant">
                Not Available Yet
              </span>
              <p className="font-body-sm text-body-sm text-on-surface-variant">
                Rewritten bullet suggestions are not generated anywhere in this project — there is no AI
                capability in the backend, so none are shown.
              </p>
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
}
