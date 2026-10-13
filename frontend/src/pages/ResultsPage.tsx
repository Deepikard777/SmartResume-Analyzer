import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  Badge,
  Card,
  ErrorPanel,
  LoadingPanel,
  ProgressBar,
  ScoreDial,
  SectionLabel,
  type BadgeTone,
} from '../components/ui';
import { useAsync } from '../hooks/useAsync';
import { analysisService } from '../services/analysisService';
import { describeError, saveBlobAsFile } from '../services/api';
import type { AiStatus, Analysis, StructureCheck } from '../types/api';
import { formatDateTime, levelLabel, scoreBand } from '../utils/format';
import { fallbackDownloadName, fileNameFromContentDisposition } from '../utils/http';
import {
  buildKeywordRows,
  relativeBarPercent,
  statusTone,
  summariseKeywords,
} from '../utils/keywords';

type TabKey = 'all' | 'skills' | 'keywords' | 'frequency' | 'structure' | 'recommendations';

interface MetricCard {
  label: string;
  icon: string;
  value: number | null;
  caption: string;
  barClass: string;
  tone: 'success' | 'warning';
}

export function ResultsPage() {
  const { analysisId } = useParams<{ analysisId: string }>();
  const numericId = Number(analysisId);

  const state = useAsync<Analysis | null>(
    async () => {
      if (!Number.isFinite(numericId)) return null;
      return analysisService.getById(numericId);
    },
    [numericId],
  );

  const [tab, setTab] = useState<TabKey>('all');
  const [copiedKeyword, setCopiedKeyword] = useState<string | null>(null);

  // Report export state.
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const [exportedName, setExportedName] = useState<string | null>(null);

  // AI state. `refreshed` holds the payload returned by a successful generation so
  // the page updates in place instead of flashing a full-page loader.
  const [refreshed, setRefreshed] = useState<Analysis | null>(null);
  const [aiBusy, setAiBusy] = useState(false);
  const [aiError, setAiError] = useState<string | null>(null);

  const aiStatusState = useAsync<AiStatus | null>(async () => {
    try {
      return await analysisService.aiStatus();
    } catch {
      // A status probe failure must never break the results page.
      return null;
    }
  }, []);

  const analysis = refreshed ?? state.data ?? null;

  const derived = useMemo(() => {
    if (!analysis) return null;

    const matchedSkills = analysis.matchedSkills ?? [];
    const missingSkills = analysis.missingSkills ?? [];
    const optionalSkills = analysis.optionalSkills ?? [];
    const matchedKeywords = analysis.matchedKeywords ?? [];
    const missingKeywords = analysis.missingKeywords ?? [];
    const structure: StructureCheck[] = analysis.structure ?? [];
    const keywordRows = buildKeywordRows(analysis.keywordCoverage);
    const keywordSummary = summariseKeywords(keywordRows);
    const aiRecommendations = analysis.aiRecommendations ?? [];

    const totalSkills = matchedSkills.length + missingSkills.length + optionalSkills.length;
    const totalKeywords = matchedKeywords.length + missingKeywords.length;
    const passedSections = structure.filter((check) => check.status === 'PASSED').length;
    const warnedSections = structure.filter((check) => check.status === 'WARNING').length;

    return {
      matchedSkills,
      missingSkills,
      optionalSkills,
      matchedKeywords,
      missingKeywords,
      structure,
      keywordRows,
      keywordSummary,
      aiRecommendations,
      totalSkills,
      totalKeywords,
      passedSections,
      warnedSections,
    };
  }, [analysis]);

  if (state.loading) {
    return (
      <div className="w-full px-gutter-mobile lg:px-gutter py-space-lg">
        <LoadingPanel label="Loading the stored analysis…" />
      </div>
    );
  }

  if (state.error) {
    return (
      <div className="w-full px-gutter-mobile lg:px-gutter py-space-lg">
        <ErrorPanel message={state.error} onRetry={state.reload} />
      </div>
    );
  }

  if (!analysis || !derived) {
    return (
      <div className="w-full px-gutter-mobile lg:px-gutter py-space-lg">
        <ErrorPanel message="This analysis could not be loaded. It may have been removed." />
      </div>
    );
  }

  const band = scoreBand(analysis.atsScore);
  const breakdown = analysis.scoreBreakdown;
  const stuffing = analysis.keywordStuffing;
  const flaggedKeywords = stuffing?.flaggedKeywords ?? [];
  const stuffingRisk = stuffing?.risk ?? null;
  const recommendations = analysis.recommendations ?? [];

  const metrics: MetricCard[] = [
    {
      label: 'Skills',
      icon: 'verified',
      value: breakdown.skillMatch,
      caption:
        derived.totalSkills > 0
          ? `${derived.matchedSkills.length} of ${derived.totalSkills} recognised skills match`
          : 'No recognised skills in the job description',
      barClass: 'bg-secondary',
      tone: 'success',
    },
    {
      label: 'Keywords',
      icon: 'key',
      value: breakdown.keywordMatch,
      caption:
        derived.totalKeywords > 0
          ? `${derived.matchedKeywords.length} of ${derived.totalKeywords} JD keywords found`
          : 'No keywords extracted from the job description',
      barClass: 'bg-secondary',
      tone: 'success',
    },
    {
      label: 'Relevance',
      icon: 'timeline',
      value: breakdown.relevance,
      caption: 'Weighted by the JD\u2019s own keyword emphasis',
      barClass: 'bg-tertiary-container',
      tone: 'warning',
    },
    {
      label: 'Structure',
      icon: 'view_quilt',
      value: breakdown.structure,
      caption: `${derived.passedSections} of ${derived.structure.length} sections detected`,
      barClass: 'bg-secondary',
      tone: 'success',
    },
    {
      label: 'Formatting',
      icon: 'check_box',
      value: breakdown.formatting,
      caption: 'Quality of the extracted resume text',
      barClass: 'bg-secondary',
      tone: 'success',
    },
  ];

  /**
   * Download the PDF report. The endpoint needs the bearer token, so the file is
   * fetched as a blob (not via an <a href>) and handed to the browser afterwards.
   */
  const handleExport = async () => {
    setExporting(true);
    setExportError(null);
    setExportedName(null);
    try {
      const { blob, contentDisposition } = await analysisService.downloadReport(analysis.id);
      const fileName =
        fileNameFromContentDisposition(contentDisposition) ?? fallbackDownloadName(analysis.id);
      saveBlobAsFile(blob, fileName);
      setExportedName(fileName);
    } catch (error) {
      setExportError(describeError(error));
    } finally {
      setExporting(false);
    }
  };

  /** Ask the provider for AI suggestions, then refresh the payload in place. */
  const handleGenerateAi = async () => {
    setAiBusy(true);
    setAiError(null);
    try {
      const updated = await analysisService.generateAiRecommendations(analysis.id);
      setRefreshed(updated);
    } catch (error) {
      setAiError(describeError(error));
    } finally {
      setAiBusy(false);
    }
  };

  const tabs: { key: TabKey; label: string; icon: string; badge?: { text: string; className: string } }[] = [
    { key: 'all', label: 'Full Diagnostic View', icon: 'dashboard' },
    {
      key: 'skills',
      label: 'Skill Match Breakdown',
      icon: 'code',
      badge: { text: `${breakdown.skillMatch ?? 0}%`, className: 'bg-primary/10 text-primary' },
    },
    {
      key: 'keywords',
      label: 'Keyword Density & Stuffing',
      icon: 'find_in_page',
      badge: {
        text: `${flaggedKeywords.length} ${flaggedKeywords.length === 1 ? 'Alert' : 'Alerts'}`,
        className:
          flaggedKeywords.length > 0 ? 'bg-tertiary-fixed text-tertiary' : 'bg-surface-container-high text-on-surface-variant',
      },
    },
    {
      key: 'frequency',
      label: 'Keyword Frequency',
      icon: 'bar_chart',
      badge: {
        text: `${derived.keywordSummary.coveragePercent}%`,
        className: 'bg-primary/10 text-primary',
      },
    },
    { key: 'structure', label: 'Structure Audit', icon: 'rule' },
    {
      key: 'recommendations',
      label: 'Recommendations',
      icon: 'task_alt',
      badge: recommendations.length
        ? { text: `${recommendations.length}`, className: 'bg-primary/10 text-primary' }
        : undefined,
    },
  ];

  const showSection = (key: Exclude<TabKey, 'all'>) => tab === 'all' || tab === key;

  const copyKeyword = async (keyword: string) => {
    try {
      await navigator.clipboard.writeText(keyword);
      setCopiedKeyword(keyword);
      window.setTimeout(() => setCopiedKeyword(null), 2200);
    } catch {
      setCopiedKeyword(null);
    }
  };

  return (
    <div className="w-full px-gutter-mobile lg:px-gutter py-space-lg flex flex-col gap-space-xl">
      {/* Metadata banner */}
      <div className="relative overflow-hidden rounded-xl bg-surface-container-lowest p-space-md lg:p-space-lg shadow-sm">
        <div className="absolute -right-24 -top-24 w-80 h-80 bg-primary/5 rounded-full blur-3xl pointer-events-none" />
        <div className="relative z-10 flex flex-col xl:flex-row xl:items-center justify-between gap-space-md">
          <div className="flex flex-col gap-space-xs">
            <div className="flex flex-wrap items-center gap-space-xs">
              <span className="px-space-xs py-0.5 rounded-DEFAULT bg-primary-fixed text-on-primary-fixed-variant font-label-badge text-label-badge uppercase font-medium">
                Diagnostic Report
              </span>
              <span className="text-outline-variant font-label-code text-label-code">•</span>
              <div className="flex items-center gap-1 text-on-surface-variant font-label-code text-label-code">
                <span className="material-symbols-outlined text-[16px] text-primary">description</span>
                <span className="font-medium text-on-surface">{analysis.resumeFileName}</span>
              </div>
            </div>
            <h1 className="font-headline-md text-headline-md text-on-surface font-semibold tracking-tight mt-1 flex flex-wrap items-center gap-space-sm">
              <span>{analysis.jobTitle}</span>
              {analysis.companyName && (
                <>
                  <span className="text-on-surface-variant font-normal font-body-lg text-body-lg">at</span>
                  <span className="px-space-sm py-0.5 rounded-lg bg-surface-container font-headline-sm text-headline-sm text-primary font-bold">
                    {analysis.companyName}
                  </span>
                </>
              )}
            </h1>
            <div className="flex items-center gap-space-sm font-body-sm text-body-sm text-on-surface-variant mt-0.5 flex-wrap">
              <span className="flex items-center gap-1">
                <span className="w-2 h-2 rounded-full bg-secondary" />
                Analysis #{analysis.id} completed {formatDateTime(analysis.createdAt)}
              </span>
              <span>•</span>
              <span className={band.label === 'Needs Work' ? 'text-tertiary font-medium' : 'text-secondary font-medium'}>
                {band.label} match
              </span>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-space-xs lg:gap-space-sm pt-space-xs xl:pt-0">
            <Link
              to="/analysis/history"
              className="inline-flex items-center gap-1.5 px-space-md py-2 rounded-lg bg-surface-container text-on-surface font-body-md text-body-md font-medium hover:bg-surface-container-high transition-all shadow-sm"
            >
              <span className="material-symbols-outlined text-[18px] text-primary">history</span>
              <span>Analysis History</span>
            </Link>
            <Link
              to="/analysis/new"
              className="inline-flex items-center gap-1.5 px-space-md py-2 rounded-lg bg-surface-container-low text-on-surface font-body-md text-body-md font-medium hover:bg-surface-container transition-all"
            >
              <span className="material-symbols-outlined text-[18px] text-on-surface-variant">sync_alt</span>
              <span>Re-analyze with new JD</span>
            </Link>
            <button
              className={`inline-flex items-center gap-1.5 px-space-md py-2 rounded-lg font-body-md text-body-md font-medium transition-all ${
                exporting
                  ? 'bg-surface-container text-on-surface-variant opacity-70 cursor-wait'
                  : 'bg-primary-container text-on-primary hover:opacity-90'
              }`}
              type="button"
              onClick={handleExport}
              disabled={exporting}
              title="Download this analysis as a printable PDF report"
            >
              <span className="material-symbols-outlined text-[18px]">
                {exporting ? 'hourglass_top' : 'download'}
              </span>
              <span>{exporting ? 'Preparing report…' : 'Export PDF Report'}</span>
            </button>
          </div>
        </div>
      </div>

      {/* Report export feedback */}
      {(exportError || exportedName) && (
        <div
          className={`px-space-md py-2 rounded-lg font-body-sm text-body-sm flex items-start gap-2 ${
            exportError
              ? 'bg-tertiary-fixed/30 text-tertiary'
              : 'bg-secondary-container text-on-secondary-container'
          }`}
          role="status"
        >
          <span className="material-symbols-outlined text-[18px] shrink-0 mt-0.5">
            {exportError ? 'report_problem' : 'check_circle'}
          </span>
          <span>
            {exportError ?? `Report downloaded as ${exportedName}.`}
          </span>
        </div>
      )}

      {/* Score + metric breakdown */}
      <div className="grid grid-cols-1 xl:grid-cols-12 gap-space-md">
        <Card className="xl:col-span-5 flex flex-col justify-between relative overflow-hidden">
          <div className="flex items-start justify-between gap-space-sm">
            <div>
              <SectionLabel>Comprehensive Readiness</SectionLabel>
              <h2 className="font-headline-sm text-headline-sm text-on-surface mt-0.5">ATS Diagnostic Score</h2>
            </div>
            <span
              className={`inline-flex items-center gap-1 px-space-sm py-1 rounded-DEFAULT font-label-badge text-label-badge uppercase font-semibold ${band.className}`}
            >
              <span className="material-symbols-outlined text-[14px]">auto_graph</span>
              {band.label}
            </span>
          </div>

          <div className="my-space-md flex flex-col sm:flex-row items-center gap-space-lg">
            <ScoreDial value={analysis.atsScore} size="lg" innerValue={breakdown.skillMatch}>
              <div className="flex items-baseline">
                <span className="font-display text-[2.5rem] leading-none font-bold text-on-surface">
                  {analysis.atsScore}
                </span>
                <span className="font-body-sm text-body-sm text-on-surface-variant font-medium">/100</span>
              </div>
              <span className="font-label-badge text-label-badge text-secondary font-semibold uppercase mt-0.5">
                {band.label}
              </span>
            </ScoreDial>

            <div className="flex flex-col gap-space-xs text-center sm:text-left min-w-0">
              <div className="inline-flex items-center gap-1.5 px-space-xs py-0.5 rounded-DEFAULT bg-secondary-container text-on-secondary-container font-label-badge text-label-badge font-semibold self-center sm:self-start">
                <span className="w-1.5 h-1.5 rounded-full bg-secondary animate-pulse" />
                {derived.matchedSkills.length} matched / {derived.missingSkills.length} missing skills
              </div>
              <p className="font-body-md text-body-md text-on-surface-variant leading-relaxed">
                This score is the fixed weighted blend of the five metrics shown beside it, computed by the
                backend engine and stored with this analysis. It is a project-defined heuristic, not a
                commercial ATS benchmark.
              </p>
            </div>
          </div>

          <div className="pt-space-sm bg-surface-container-low/60 -mx-space-lg -mb-space-lg px-space-lg py-space-sm flex items-center justify-between font-label-code text-label-code text-on-surface-variant">
            <div className="flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-sm bg-primary-container" />
              <span>Overall ATS ({analysis.atsScore}%)</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-sm bg-secondary" />
              <span>Skill Alignment ({breakdown.skillMatch ?? 0}%)</span>
            </div>
          </div>
        </Card>

        <div className="xl:col-span-7 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-space-sm">
          {metrics.map((metric) => (
            <div
              key={metric.label}
              className="rounded-xl bg-surface-container-lowest p-space-md shadow-sm flex flex-col justify-between hover:-translate-y-0.5 transition-transform duration-200"
            >
              <div className="flex items-center justify-between">
                <span
                  className={`w-7 h-7 rounded-lg flex items-center justify-center ${
                    metric.tone === 'warning'
                      ? 'bg-tertiary-fixed text-tertiary'
                      : 'bg-surface-container text-primary'
                  }`}
                >
                  <span className="material-symbols-outlined text-[18px]">{metric.icon}</span>
                </span>
                <span
                  className={`font-label-badge text-label-badge uppercase font-semibold ${
                    metric.tone === 'warning' ? 'text-tertiary' : 'text-secondary'
                  }`}
                >
                  {levelLabel(metric.value ?? 0)}
                </span>
              </div>
              <div className="mt-space-md">
                <div className="flex items-baseline justify-between gap-2 mb-1">
                  <span className="font-label-code text-label-code text-on-surface-variant truncate">
                    {metric.label}
                  </span>
                  <span className="font-headline-sm text-headline-sm font-bold text-on-surface shrink-0">
                    {metric.value ?? 0}%
                  </span>
                </div>
                <ProgressBar value={metric.value ?? 0} barClassName={metric.barClass} heightClassName="h-1.5" />
                <p className="font-body-sm text-body-sm text-on-surface-variant mt-2 leading-tight">
                  {metric.caption}
                </p>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Tabs */}
      <div className="flex items-center justify-between bg-surface-container-low p-1.5 rounded-xl shadow-inner overflow-x-auto">
        <div className="flex items-center gap-1 min-w-max" role="tablist">
          {tabs.map((item) => {
            const isActive = tab === item.key;
            return (
              <button
                key={item.key}
                type="button"
                role="tab"
                aria-selected={isActive}
                onClick={() => setTab(item.key)}
                className={`px-space-md py-2 rounded-lg font-body-md text-body-md transition-all ${
                  isActive
                    ? 'bg-surface-container-lowest text-primary font-semibold shadow-sm'
                    : 'text-on-surface-variant font-medium hover:text-on-surface'
                }`}
              >
                <span className="flex items-center gap-1.5">
                  <span className="material-symbols-outlined text-[18px]">{item.icon}</span>
                  {item.label}
                  {item.badge && (
                    <span className={`px-1.5 py-0.5 rounded-full font-label-badge text-label-badge ${item.badge.className}`}>
                      {item.badge.text}
                    </span>
                  )}
                </span>
              </button>
            );
          })}
        </div>
        <div className="hidden lg:flex items-center gap-space-sm pl-space-md font-label-code text-label-code text-on-surface-variant">
          <span>{tab === 'all' ? 'Displaying all sections' : 'Filtered view'}</span>
        </div>
      </div>

      {/* Sections */}
      <div className="grid grid-cols-1 xl:grid-cols-12 gap-space-lg items-start">
        <div className="xl:col-span-7 flex flex-col gap-space-lg">
          {/* 1. SKILLS */}
          {showSection('skills') && (
            <Card className="flex flex-col gap-space-md" id="section-skills">
              <div className="flex items-center justify-between pb-space-xs gap-space-sm flex-wrap">
                <div className="flex items-center gap-space-sm">
                  <div className="w-8 h-8 rounded-lg bg-surface-container flex items-center justify-center text-primary">
                    <span className="material-symbols-outlined text-[20px]">psychology</span>
                  </div>
                  <div>
                    <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                      1. Technical Skill Alignment
                    </h3>
                    <p className="font-body-sm text-body-sm text-on-surface-variant">
                      Matched against {analysis.jobTitle}
                      {analysis.companyName ? ` at ${analysis.companyName}` : ''}
                    </p>
                  </div>
                </div>
                <span className="font-label-badge text-label-badge text-secondary bg-secondary-container px-space-xs py-1 rounded-DEFAULT font-semibold uppercase">
                  {derived.matchedSkills.length} / {derived.totalSkills} matched
                </span>
              </div>

              <div className="flex flex-col gap-space-xs bg-surface-container-low/50 p-space-md rounded-xl">
                <span className="font-label-badge text-label-badge uppercase font-semibold text-secondary flex items-center gap-1">
                  <span className="material-symbols-outlined text-[16px]">check_circle</span>
                  Matched skills found in your resume
                </span>
                {derived.matchedSkills.length === 0 ? (
                  <p className="font-body-sm text-body-sm text-on-surface-variant pt-1">
                    No recognised skills from the job description were found in the resume text.
                  </p>
                ) : (
                  <div className="flex flex-wrap gap-2 pt-1">
                    {derived.matchedSkills.map((skill) => (
                      <span
                        key={skill}
                        className="inline-flex items-center gap-1.5 px-space-sm py-1 rounded-DEFAULT bg-secondary-container text-on-secondary-container font-label-badge text-label-badge font-medium"
                      >
                        <span className="material-symbols-outlined text-[14px] text-secondary">check</span>
                        {skill}
                      </span>
                    ))}
                  </div>
                )}
              </div>

              <div className="flex flex-col gap-space-xs bg-tertiary-fixed/30 p-space-md rounded-xl">
                <span className="font-label-badge text-label-badge uppercase font-semibold text-tertiary flex items-center gap-1">
                  <span className="material-symbols-outlined text-[16px]">error</span>
                  Missing skills required by the job description
                </span>
                {derived.missingSkills.length === 0 ? (
                  <p className="font-body-sm text-body-sm text-on-surface-variant pt-1">
                    None — every recognised skill in the job description appears in your resume.
                  </p>
                ) : (
                  <div className="flex flex-wrap gap-2 pt-1">
                    {derived.missingSkills.map((skill) => (
                      <button
                        key={skill}
                        type="button"
                        onClick={() => void copyKeyword(skill)}
                        title="Click to copy this skill name"
                        className="inline-flex items-center gap-1.5 px-space-sm py-1 rounded-DEFAULT bg-surface-container-lowest text-tertiary-container font-label-badge text-label-badge font-semibold shadow-sm hover:bg-tertiary-fixed transition-colors"
                      >
                        <span className="material-symbols-outlined text-[14px] text-tertiary">warning</span>
                        <span>{skill}</span>
                        <span className="material-symbols-outlined text-[14px] text-on-surface-variant">
                          content_copy
                        </span>
                      </button>
                    ))}
                  </div>
                )}
                {copiedKeyword && (
                  <span className="font-label-code text-label-code text-secondary">
                    "{copiedKeyword}" copied to clipboard
                  </span>
                )}
              </div>

              <div className="flex flex-col gap-space-xs bg-surface-container-low/40 p-space-md rounded-xl">
                <span className="font-label-badge text-label-badge uppercase font-semibold text-on-surface-variant flex items-center gap-1">
                  <span className="material-symbols-outlined text-[16px] text-primary">stars</span>
                  Optional / nice-to-have skills from the job description
                </span>
                {derived.optionalSkills.length === 0 ? (
                  <p className="font-body-sm text-body-sm text-on-surface-variant pt-1">
                    The job description did not flag any recognised skill as optional.
                  </p>
                ) : (
                  <div className="flex flex-wrap gap-2 pt-1">
                    {derived.optionalSkills.map((skill) => (
                      <span
                        key={skill}
                        className="inline-flex items-center gap-1 px-space-sm py-0.5 rounded-DEFAULT bg-surface-container text-on-surface font-label-badge text-label-badge"
                      >
                        {skill}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </Card>
          )}

          {/* 2. KEYWORDS */}
          {showSection('keywords') && (
            <Card className="flex flex-col gap-space-md" id="section-keywords">
              <div className="flex items-center justify-between pb-space-xs gap-space-sm flex-wrap">
                <div className="flex items-center gap-space-sm">
                  <div className="w-8 h-8 rounded-lg bg-surface-container flex items-center justify-center text-primary">
                    <span className="material-symbols-outlined text-[20px]">manage_search</span>
                  </div>
                  <div>
                    <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                      2. Keyword Coverage &amp; Stuffing Telemetry
                    </h3>
                    <p className="font-body-sm text-body-sm text-on-surface-variant">
                      Extracted from the job description text and matched against your resume
                    </p>
                  </div>
                </div>
                <span
                  className={`inline-flex items-center gap-1 px-space-sm py-1 rounded-DEFAULT font-label-badge text-label-badge uppercase font-semibold ${
                    stuffingRisk === 'HIGH'
                      ? 'bg-tertiary-container text-on-tertiary'
                      : stuffingRisk === 'MEDIUM'
                        ? 'bg-tertiary-fixed text-tertiary'
                        : 'bg-secondary-container text-on-secondary-container'
                  }`}
                >
                  <span className="material-symbols-outlined text-[14px]">
                    {stuffingRisk === 'LOW' ? 'check_circle' : 'warning'}
                  </span>
                  {stuffingRisk ?? 'Unknown'} risk
                </span>
              </div>

              {flaggedKeywords.length > 0 ? (
                <div className="p-space-md rounded-xl bg-surface-container-low flex flex-col gap-space-xs">
                  <span className="font-body-lg text-body-lg font-semibold text-on-surface">
                    {flaggedKeywords.length} keyword{flaggedKeywords.length === 1 ? '' : 's'} flagged for
                    repetition
                  </span>
                  {flaggedKeywords.map((flagged) => (
                    <div key={flagged.keyword} className="flex flex-col">
                      <span className="font-label-code text-label-code font-bold text-on-surface">
                        {flagged.keyword}
                      </span>
                      <p className="font-body-md text-body-md text-on-surface-variant leading-relaxed">
                        {flagged.reason}
                      </p>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="p-space-md rounded-xl bg-surface-container-low flex items-start gap-space-sm">
                  <span className="material-symbols-outlined text-secondary text-[22px] shrink-0 mt-0.5">
                    check_circle
                  </span>
                  <p className="font-body-md text-body-md text-on-surface-variant leading-relaxed">
                    No keyword repetition crossed the project&apos;s stuffing thresholds in this resume.
                  </p>
                </div>
              )}

              <div className="grid grid-cols-1 md:grid-cols-2 gap-space-sm">
                <div className="p-space-md rounded-lg bg-surface-container-low/50 flex flex-col gap-space-xs">
                  <span className="font-label-badge text-label-badge uppercase font-semibold text-secondary">
                    Matched keywords ({derived.matchedKeywords.length})
                  </span>
                  <div className="flex flex-wrap gap-1.5">
                    {derived.matchedKeywords.length === 0 ? (
                      <span className="font-body-sm text-body-sm text-on-surface-variant">
                        None of the extracted JD keywords appear in the resume.
                      </span>
                    ) : (
                      derived.matchedKeywords.map((keyword) => (
                        <span
                          key={keyword}
                          className="px-space-xs py-0.5 rounded-DEFAULT bg-secondary-container text-on-secondary-container font-label-badge text-label-badge"
                        >
                          {keyword}
                        </span>
                      ))
                    )}
                  </div>
                </div>
                <div className="p-space-md rounded-lg bg-tertiary-fixed/20 flex flex-col gap-space-xs">
                  <span className="font-label-badge text-label-badge uppercase font-semibold text-tertiary">
                    Missing keywords ({derived.missingKeywords.length})
                  </span>
                  <div className="flex flex-wrap gap-1.5">
                    {derived.missingKeywords.length === 0 ? (
                      <span className="font-body-sm text-body-sm text-on-surface-variant">
                        Every extracted JD keyword was found in the resume.
                      </span>
                    ) : (
                      derived.missingKeywords.map((keyword) => (
                        <span
                          key={keyword}
                          className="px-space-xs py-0.5 rounded-DEFAULT bg-surface-container-lowest text-tertiary font-label-badge text-label-badge"
                        >
                          {keyword}
                        </span>
                      ))
                    )}
                  </div>
                </div>
              </div>

              <p className="font-body-sm text-body-sm text-on-surface-variant">
                Per-keyword counts, coverage and repetition indicators are on the
                <button
                  type="button"
                  className="text-primary font-medium underline decoration-dotted mx-1"
                  onClick={() => setTab('frequency')}
                >
                  Keyword Frequency
                </button>
                tab.
              </p>
            </Card>
          )}

          {/* 2b. KEYWORD FREQUENCY */}
          {showSection('frequency') && (
            <Card className="flex flex-col gap-space-md" id="section-frequency">
              <div className="flex items-center justify-between pb-space-xs gap-space-sm flex-wrap">
                <div className="flex items-center gap-space-sm">
                  <div className="w-8 h-8 rounded-lg bg-surface-container flex items-center justify-center text-primary">
                    <span className="material-symbols-outlined text-[20px]">bar_chart</span>
                  </div>
                  <div>
                    <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                      2b. Keyword Frequency &amp; Coverage
                    </h3>
                    <p className="font-body-sm text-body-sm text-on-surface-variant">
                      Whole-word counts, taken from the job description and your resume text
                    </p>
                  </div>
                </div>
                <Badge tone="neutral">
                  {derived.keywordSummary.coveragePercent}% of JD terms covered
                </Badge>
              </div>

              {derived.keywordRows.length === 0 ? (
                <div className="p-space-md rounded-xl bg-surface-container-low flex items-start gap-space-sm">
                  <span className="material-symbols-outlined text-on-surface-variant text-[22px] shrink-0 mt-0.5">
                    info
                  </span>
                  <p className="font-body-md text-body-md text-on-surface-variant leading-relaxed">
                    No per-keyword frequency data was stored with this analysis — it was created before this view
                    existed. Run the analysis again to populate it.
                  </p>
                </div>
              ) : (
                <>
                  {/* Summary tiles */}
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-space-sm">
                    {[
                      {
                        label: 'Keywords in JD',
                        value: derived.keywordSummary.fromJobDescription,
                        tone: 'text-on-surface',
                      },
                      {
                        label: 'Present in resume',
                        value: derived.keywordSummary.matched,
                        tone: 'text-secondary',
                      },
                      {
                        label: 'Missing',
                        value: derived.keywordSummary.missing,
                        tone: 'text-tertiary',
                      },
                      {
                        label: 'Flagged for repetition',
                        value: derived.keywordSummary.repeated,
                        tone: 'text-on-surface',
                      },
                    ].map((tile) => (
                      <div
                        key={tile.label}
                        className="rounded-xl bg-surface-container-low p-space-sm flex flex-col gap-0.5"
                      >
                        <span className={`font-headline-sm text-headline-sm font-bold ${tile.tone}`}>
                          {tile.value}
                        </span>
                        <span className="font-label-badge text-label-badge text-on-surface-variant">
                          {tile.label}
                        </span>
                      </div>
                    ))}
                  </div>

                  {/* Coverage bar */}
                  <div className="flex flex-col gap-1">
                    <div className="flex items-center justify-between font-label-badge text-label-badge text-on-surface-variant">
                      <span>Job-description keyword coverage</span>
                      <span>
                        {derived.keywordSummary.matched} / {derived.keywordSummary.fromJobDescription}
                      </span>
                    </div>
                    <ProgressBar
                      value={derived.keywordSummary.coveragePercent}
                      barClassName="bg-secondary"
                      heightClassName="h-2"
                    />
                  </div>

                  {/* Table — horizontally scrollable on narrow screens */}
                  <div className="-mx-space-md px-space-md overflow-x-auto">
                    <table className="w-full min-w-[600px] border-collapse">
                      <thead>
                        <tr className="text-left">
                          <th className="font-label-badge text-label-badge uppercase font-semibold text-on-surface-variant pb-2">
                            Keyword
                          </th>
                          <th className="font-label-badge text-label-badge uppercase font-semibold text-on-surface-variant pb-2">
                            Times in JD
                          </th>
                          <th className="font-label-badge text-label-badge uppercase font-semibold text-on-surface-variant pb-2">
                            Times in resume
                          </th>
                          <th className="font-label-badge text-label-badge uppercase font-semibold text-on-surface-variant pb-2">
                            Status
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {derived.keywordRows.map((row) => (
                          <tr key={row.keyword} className="border-t border-outline-variant/40">
                            <td className="py-2 pr-3 font-label-code text-label-code text-on-surface">
                              {row.keyword}
                              {row.flagReason && (
                                <span className="block font-body-sm text-body-sm text-on-surface-variant">
                                  {row.flagReason}
                                </span>
                              )}
                            </td>
                            <td className="py-2 pr-3">
                              <div className="flex items-center gap-2">
                                <div className="w-16 h-1.5 rounded-full bg-surface-container overflow-hidden">
                                  <div
                                    className="h-full bg-primary/60"
                                    style={{
                                      width: `${relativeBarPercent(row.jobDescriptionCount, Math.max(1, derived.keywordSummary.fromJobDescription))}%`,
                                    }}
                                  />
                                </div>
                                <span className="font-label-code text-label-code text-on-surface-variant">
                                  {row.jobDescriptionCount}
                                </span>
                              </div>
                            </td>
                            <td className="py-2 pr-3">
                              <div className="flex items-center gap-2">
                                <div className="w-16 h-1.5 rounded-full bg-surface-container overflow-hidden">
                                  <div
                                    className={
                                      row.resumeCount === 0
                                        ? 'h-full bg-transparent'
                                        : row.flagged
                                          ? 'h-full bg-tertiary'
                                          : 'h-full bg-secondary'
                                    }
                                    style={{
                                      width: `${relativeBarPercent(row.resumeCount, derived.keywordSummary.maxResumeCount)}%`,
                                    }}
                                  />
                                </div>
                                <span className="font-label-code text-label-code text-on-surface">
                                  {row.resumeCount}
                                </span>
                              </div>
                            </td>
                            <td className="py-2">
                              <Badge tone={statusTone(row.status)}>{row.statusLabel}</Badge>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>

                  <p className="font-body-sm text-body-sm text-on-surface-variant leading-relaxed">
                    Counts are whole-word occurrences. Repetition is shown as an advisory indicator only — a Java
                    developer&apos;s resume is expected to repeat &quot;java&quot;, and the score is not reduced
                    just because a term appears several times. A row is marked
                    <span className="font-semibold"> Repeated </span>
                    only when the backend repetition detector&apos;s documented thresholds were crossed, and the raw
                    counts are always shown so you can judge for yourself.
                  </p>
                </>
              )}
            </Card>
          )}

          {/* 3. STRUCTURE */}
          {showSection('structure') && (
            <Card className="flex flex-col gap-space-md" id="section-structure">
              <div className="flex items-center justify-between pb-space-xs gap-space-sm flex-wrap">
                <div className="flex items-center gap-space-sm">
                  <div className="w-8 h-8 rounded-lg bg-surface-container flex items-center justify-center text-primary">
                    <span className="material-symbols-outlined text-[20px]">fact_check</span>
                  </div>
                  <div>
                    <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                      3. Resume Parser Structural Audit
                    </h3>
                    <p className="font-body-sm text-body-sm text-on-surface-variant">
                      Section headings detected in the extracted resume text
                    </p>
                  </div>
                </div>
                <span className="font-label-badge text-label-badge text-secondary bg-secondary-container px-space-xs py-1 rounded-DEFAULT font-semibold uppercase">
                  {derived.passedSections} Passed / {derived.warnedSections} Warnings
                </span>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-space-sm">
                {derived.structure.map((check) => {
                  const passed = check.status === 'PASSED';
                  return (
                    <div
                      key={check.section}
                      className={`p-space-md rounded-xl flex items-start justify-between gap-2 ${
                        passed ? 'bg-surface-container-low/40' : 'bg-tertiary-fixed/20'
                      }`}
                    >
                      <div className="flex items-start gap-space-sm">
                        <span
                          className={`material-symbols-outlined text-[20px] mt-0.5 ${
                            passed ? 'text-secondary' : 'text-tertiary'
                          }`}
                        >
                          {passed ? 'check_circle' : 'warning'}
                        </span>
                        <div className="flex flex-col">
                          <span className="font-body-md text-body-md font-semibold text-on-surface">
                            {check.section}
                          </span>
                          <span className="font-body-sm text-body-sm text-on-surface-variant">{check.message}</span>
                        </div>
                      </div>
                      <span
                        className={`px-space-xs py-0.5 rounded-DEFAULT font-label-badge text-label-badge font-semibold shrink-0 ${
                          passed
                            ? 'bg-secondary-container text-on-secondary-container'
                            : 'bg-tertiary-fixed text-tertiary'
                        }`}
                      >
                        {passed ? 'Passed' : 'Warning'}
                      </span>
                    </div>
                  );
                })}
              </div>
            </Card>
          )}
        </div>

        {/* RIGHT COLUMN */}
        <div className="xl:col-span-5 flex flex-col gap-space-lg">
          {showSection('recommendations') && (
            <Card className="flex flex-col gap-space-md xl:sticky xl:top-20">
              <div className="flex items-center justify-between pb-space-xs gap-space-sm flex-wrap">
                <div className="flex items-center gap-space-sm">
                  <div className="w-8 h-8 rounded-lg bg-primary-container flex items-center justify-center text-on-primary">
                    <span className="material-symbols-outlined text-[20px]">task_alt</span>
                  </div>
                  <div>
                    <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                      Actionable Recommendations
                    </h3>
                    <p className="font-body-sm text-body-sm text-on-surface-variant">
                      Derived from this analysis — no AI involved
                    </p>
                  </div>
                </div>
                <span className="font-label-badge text-label-badge text-on-surface-variant font-semibold uppercase">
                  {recommendations.length} {recommendations.length === 1 ? 'item' : 'items'}
                </span>
              </div>

              {recommendations.length > 0 ? (
                <div className="flex flex-col gap-space-sm">
                  {recommendations.map((recommendation, index) => {
                    const tone: BadgeTone =
                      recommendation.priority === 'HIGH'
                        ? 'danger'
                        : recommendation.priority === 'MEDIUM'
                          ? 'warning'
                          : 'neutral';
                    const related = recommendation.relatedItems ?? [];
                    return (
                      <div
                        key={`${recommendation.category}-${index}`}
                        className="p-space-md rounded-xl bg-surface-container-low flex flex-col gap-space-xs"
                      >
                        <div className="flex items-center justify-between gap-space-sm flex-wrap">
                          <Badge tone={tone}>
                            {recommendation.priority} · {recommendation.category}
                          </Badge>
                          {related.length > 0 && (
                            <span className="font-label-code text-label-code text-on-surface-variant">
                              {related.length} item{related.length === 1 ? '' : 's'}
                            </span>
                          )}
                        </div>
                        <h4 className="font-body-md text-body-md font-semibold text-on-surface leading-snug">
                          {recommendation.title}
                        </h4>
                        <p className="font-body-sm text-body-sm text-on-surface-variant">
                          {recommendation.detail}
                        </p>
                        {related.length > 0 && (
                          <div className="flex flex-wrap gap-1.5 pt-0.5">
                            {related.map((item) => (
                              <span
                                key={item}
                                className="px-2 py-0.5 rounded-DEFAULT bg-surface-container font-label-code text-label-code text-on-surface-variant"
                              >
                                {item}
                              </span>
                            ))}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className="p-space-md rounded-xl bg-surface-container-low flex flex-col items-center gap-space-sm text-center">
                  <span className="material-symbols-outlined text-[32px] text-secondary">check_circle</span>
                  <span className="font-headline-sm text-headline-sm text-on-surface">
                    Nothing to act on for this analysis.
                  </span>
                  <p className="font-body-sm text-body-sm text-on-surface-variant">
                    No missing required skills, no flagged repetition, no structure warnings and no sub-score
                    below the thresholds the generator checks were found in this run.
                  </p>
                </div>
              )}

              <div className="flex flex-col gap-space-sm">
                <SectionLabel>The Measurements Behind These Recommendations</SectionLabel>

                {derived.missingSkills.length === 0 &&
                flaggedKeywords.length === 0 &&
                derived.warnedSections === 0 && (
                  <p className="font-body-sm text-body-sm text-on-surface-variant p-space-sm rounded-lg bg-surface-container-low">
                    No gaps were detected by the deterministic checks for this analysis.
                  </p>
                )}

                {derived.missingSkills.length > 0 && (
                  <div className="p-space-md rounded-xl bg-surface-container-low flex flex-col gap-space-xs">
                    <div className="flex items-center justify-between">
                      <Badge tone="danger">High priority</Badge>
                      <span className="font-label-code text-label-code text-on-surface-variant">
                        {derived.missingSkills.length} skills
                      </span>
                    </div>
                    <h4 className="font-body-md text-body-md font-semibold text-on-surface leading-snug">
                      Add the missing required skills, or evidence of them, to your resume
                    </h4>
                    <p className="font-body-sm text-body-sm text-on-surface-variant">
                      <span className="font-semibold text-on-surface">Why it matters:</span> the job
                      description requires {derived.missingSkills.join(', ')}, and none of them were found in
                      the resume text.
                    </p>
                  </div>
                )}

                {flaggedKeywords.length > 0 && (
                  <div className="p-space-md rounded-xl bg-surface-container-low flex flex-col gap-space-xs">
                    <div className="flex items-center justify-between">
                      <Badge tone="warning">Repetition risk</Badge>
                      <span className="font-label-code text-label-code text-on-surface-variant">
                        {stuffingRisk} risk
                      </span>
                    </div>
                    <h4 className="font-body-md text-body-md font-semibold text-on-surface leading-snug">
                      Reduce repetition of flagged keywords
                    </h4>
                    <p className="font-body-sm text-body-sm text-on-surface-variant">
                      <span className="font-semibold text-on-surface">Why it matters:</span>{' '}
                      {flaggedKeywords
                        .map((flagged) => `${flagged.keyword} (${flagged.frequency}\u00d7)`)
                        .join(', ')}{' '}
                      crossed the project&apos;s stuffing thresholds.
                    </p>
                  </div>
                )}

                {derived.warnedSections > 0 && (
                  <div className="p-space-md rounded-xl bg-surface-container-low flex flex-col gap-space-xs">
                    <div className="flex items-center justify-between">
                      <Badge tone="neutral">Structure</Badge>
                      <span className="font-label-code text-label-code text-on-surface-variant">
                        {derived.warnedSections} warnings
                      </span>
                    </div>
                    <h4 className="font-body-md text-body-md font-semibold text-on-surface leading-snug">
                      Add the missing resume sections
                    </h4>
                    <ul className="flex flex-col gap-1">
                      {derived.structure
                        .filter((check) => check.status === 'WARNING')
                        .map((check) => (
                          <li
                            key={check.section}
                            className="font-body-sm text-body-sm text-on-surface-variant flex items-start gap-1.5"
                          >
                            <span className="material-symbols-outlined text-[14px] text-tertiary mt-0.5">
                              warning
                            </span>
                            <span>
                              <span className="font-semibold text-on-surface">{check.section}</span> —{' '}
                              {check.message}
                            </span>
                          </li>
                        ))}
                    </ul>
                  </div>
                )}
              </div>

              {/* AI suggestions: a separate, clearly-labelled, opt-in section. It never
                  changes the score, and it is never presented as rule-based advice. */}
              <div className="pt-space-md border-t border-outline-variant/40 flex flex-col gap-space-sm">
                <div className="flex items-center justify-between gap-space-sm flex-wrap">
                  <div className="flex items-center gap-space-sm">
                    <span className="material-symbols-outlined text-primary text-[22px]">neurology</span>
                    <div>
                      <h4 className="font-body-md text-body-md font-semibold text-on-surface">
                        AI Suggestions
                      </h4>
                      <p className="font-body-sm text-body-sm text-on-surface-variant">
                        {aiStatusState.data?.enabled
                          ? `Generated on request by ${aiStatusState.data.provider ?? 'the configured provider'}${
                              aiStatusState.data.model ? ` · ${aiStatusState.data.model}` : ''
                            }. The score above is never affected.`
                          : 'Optional feature. The recommendations above are rule-based and always available.'}
                      </p>
                    </div>
                  </div>
                  {aiStatusState.data?.enabled ? (
                    <button
                      type="button"
                      onClick={handleGenerateAi}
                      disabled={aiBusy}
                      className={`inline-flex items-center gap-1.5 px-space-md py-1.5 rounded-lg font-body-sm text-body-sm font-semibold transition-all ${
                        aiBusy
                          ? 'bg-surface-container text-on-surface-variant opacity-70 cursor-wait'
                          : 'bg-primary-container text-on-primary hover:opacity-90'
                      }`}
                    >
                      <span className="material-symbols-outlined text-[16px]">
                        {aiBusy ? 'hourglass_top' : 'auto_awesome'}
                      </span>
                      {aiBusy
                        ? 'Generating…'
                        : derived.aiRecommendations.length > 0
                          ? 'Regenerate'
                          : 'Generate AI suggestions'}
                    </button>
                  ) : (
                    <Badge tone="neutral">Not configured</Badge>
                  )}
                </div>

                {!aiStatusState.data?.enabled && (
                  <div className="p-space-sm rounded-lg bg-surface-container-low flex items-start gap-2">
                    <span className="material-symbols-outlined text-on-surface-variant text-[18px] shrink-0 mt-0.5">
                      info
                    </span>
                    <p className="font-body-sm text-body-sm text-on-surface-variant">
                      {aiStatusState.data?.reason ?? 'The AI status could not be read from the server.'} Nothing
                      is sent to a third party while this feature is off, and the rule-based recommendations above
                      remain fully usable.
                    </p>
                  </div>
                )}

                {aiError && (
                  <div className="p-space-sm rounded-lg bg-tertiary-fixed/30 flex items-start gap-2">
                    <span className="material-symbols-outlined text-tertiary text-[18px] shrink-0 mt-0.5">
                      report_problem
                    </span>
                    <p className="font-body-sm text-body-sm text-tertiary">{aiError}</p>
                  </div>
                )}

                {derived.aiRecommendations.length > 0 && (
                  <div className="flex flex-col gap-space-sm">
                    <div className="flex items-center gap-2 flex-wrap">
                      <Badge tone="neutral">AI-generated</Badge>
                      <span className="font-body-sm text-body-sm text-on-surface-variant">
                        {derived.aiRecommendations.length} suggestion
                        {derived.aiRecommendations.length === 1 ? '' : 's'}
                        {analysis.aiGeneratedAt ? ` · ${formatDateTime(analysis.aiGeneratedAt)}` : ''}
                      </span>
                    </div>
                    <p className="font-body-sm text-body-sm text-on-surface-variant">
                      {aiStatusState.data?.disclaimer ??
                        'Advisory only. Check that anything you add is genuinely yours.'}
                    </p>
                    {derived.aiRecommendations.map((recommendation, index) => (
                      <div
                        key={`ai-${index}`}
                        className="p-space-md rounded-xl bg-surface-container flex flex-col gap-space-xs"
                      >
                        <div className="flex items-center justify-between gap-space-sm flex-wrap">
                          <Badge
                            tone={
                              recommendation.priority === 'HIGH'
                                ? 'danger'
                                : recommendation.priority === 'MEDIUM'
                                  ? 'warning'
                                  : 'neutral'
                            }
                          >
                            {recommendation.priority} · {recommendation.category}
                          </Badge>
                          <span className="font-label-badge text-label-badge text-on-surface-variant uppercase">
                            AI
                          </span>
                        </div>
                        <h5 className="font-body-md text-body-md font-semibold text-on-surface leading-snug">
                          {recommendation.title}
                        </h5>
                        <p className="font-body-sm text-body-sm text-on-surface-variant">
                          {recommendation.detail}
                        </p>
                        {(recommendation.relatedItems ?? []).length > 0 && (
                          <div className="flex flex-wrap gap-1.5 pt-0.5">
                            {(recommendation.relatedItems ?? []).map((item) => (
                              <span
                                key={item}
                                className="px-2 py-0.5 rounded-DEFAULT bg-surface-container-high font-label-code text-label-code text-on-surface-variant"
                              >
                                {item}
                              </span>
                            ))}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}

                {aiStatusState.data?.enabled &&
                  !aiBusy &&
                  !aiError &&
                  derived.aiRecommendations.length === 0 && (
                    <p className="font-body-sm text-body-sm text-on-surface-variant">
                      No AI suggestions have been generated for this analysis yet.
                    </p>
                  )}
              </div>

              <div className="p-space-md rounded-xl bg-primary-fixed/40 flex items-center justify-between gap-space-sm flex-wrap">
                <div className="flex items-center gap-space-sm">
                  <span className="material-symbols-outlined text-primary text-[24px]">content_paste_off</span>
                  <div className="flex flex-col">
                    <span className="font-body-md text-body-md font-semibold text-on-surface">
                      Auto-patching your resume text
                    </span>
                    <span className="font-body-sm text-body-sm text-on-surface-variant">
                      Deliberately not offered — rewriting bullets as fact would fabricate your history
                    </span>
                  </div>
                </div>
                <button
                  className="px-space-md py-1.5 rounded-lg bg-surface-container text-on-surface-variant font-body-sm text-body-sm font-semibold cursor-not-allowed opacity-60"
                  type="button"
                  disabled
                  title="Suggestions are advice; this tool will not write your resume for you"
                >
                  Auto-Patch
                </button>
              </div>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
