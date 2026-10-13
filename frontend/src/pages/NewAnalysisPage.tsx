import { useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Card, ErrorPanel, SectionLabel } from '../components/ui';
import { useAsync } from '../hooks/useAsync';
import { describeError } from '../services/api';
import { analysisService } from '../services/analysisService';
import { jobDescriptionService } from '../services/jobDescriptionService';
import { resumeService } from '../services/resumeService';
import type { AnalysisSummary, JobDescription, Resume } from '../types/api';
import { formatBytes, formatDate, formatRelative } from '../utils/format';

const INPUT_CLASS =
  'w-full bg-surface-container-low text-on-surface font-body-md text-body-md rounded-lg py-2.5 px-space-md focus:bg-surface-container-lowest focus:ring-2 focus:ring-primary/20 focus:outline-none transition-all';

const MAX_FILE_BYTES = 10 * 1024 * 1024;
const ALLOWED_EXTENSIONS = ['pdf', 'docx'];

/** The fixed weighting actually used by AnalysisService — shown read-only. */
const SCORING_WEIGHTS = [
  { label: 'Skill Match', weight: 40 },
  { label: 'Keyword Match', weight: 25 },
  { label: 'Relevance', weight: 15 },
  { label: 'Structure', weight: 12 },
  { label: 'Formatting', weight: 8 },
];

type AnalysisStage = 'idle' | 'creating-jd' | 'analyzing';

export function NewAnalysisPage() {
  const navigate = useNavigate();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const vault = useAsync<{ resumes: Resume[]; jobDescriptions: JobDescription[]; analyses: AnalysisSummary[] }>(
    async () => {
      const [resumes, jobDescriptions, analyses] = await Promise.all([
        resumeService.list(),
        jobDescriptionService.list(),
        analysisService.list(),
      ]);
      return { resumes, jobDescriptions, analyses };
    },
    [],
  );

  const [selectedResume, setSelectedResume] = useState<Resume | null>(null);
  const [selectedVaultResumeId, setSelectedVaultResumeId] = useState<string>('');
  const [selectedJobDescriptionId, setSelectedJobDescriptionId] = useState<string>('');

  const [jobTitle, setJobTitle] = useState('');
  const [companyName, setCompanyName] = useState('');
  const [descriptionText, setDescriptionText] = useState('');

  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [stage, setStage] = useState<AnalysisStage>('idle');
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const resumes = vault.data?.resumes ?? [];
  const jobDescriptions = vault.data?.jobDescriptions ?? [];

  /** Stored analyses grouped by resume id — the summary DTO carries resumeId. */
  const analysesByResume = useMemo(() => {
    const map = new Map<number, AnalysisSummary[]>();
    for (const item of vault.data?.analyses ?? []) {
      const existing = map.get(item.resumeId);
      if (existing) {
        existing.push(item);
      } else {
        map.set(item.resumeId, [item]);
      }
    }
    return map;
  }, [vault.data]);

  /** Previous analyses for the currently selected resume (real data, no invented baseline). */
  const previousAnalyses = useMemo(() => {
    if (selectedResume === null) return null;
    return analysesByResume.get(selectedResume.id) ?? null;
  }, [selectedResume, analysesByResume]);

  const handleVaultSelect = (value: string) => {
    setSelectedVaultResumeId(value);
    setUploadError(null);
    if (value === '') {
      setSelectedResume(null);
      return;
    }
    const found = resumes.find((resume) => String(resume.id) === value) ?? null;
    setSelectedResume(found);
  };

  const handleFileChange = async (file: File | undefined) => {
    if (!file) return;
    // Re-entrancy guard: a single file selection must never start two uploads.
    if (uploading) return;
    setUploadError(null);
    setNotice(null);

    const extension = file.name.includes('.') ? file.name.split('.').pop()?.toLowerCase() ?? '' : '';
    if (!ALLOWED_EXTENSIONS.includes(extension)) {
      setUploadError(`Only PDF and DOCX resumes are supported. Received: '.${extension || 'unknown'}'.`);
      return;
    }
    if (file.size > MAX_FILE_BYTES) {
      setUploadError('Resume file must not exceed 10 MB.');
      return;
    }

    setUploading(true);
    try {
      const uploaded = await resumeService.upload(file);
      setSelectedResume(uploaded);
      setSelectedVaultResumeId('');
      setNotice(`"${uploaded.originalFileName}" uploaded and parsed by the backend.`);
      vault.reload();
    } catch (caught) {
      setUploadError(describeError(caught));
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleSubmit = async () => {
    setSubmitError(null);
    setNotice(null);

    if (selectedResume === null) {
      setSubmitError('Please upload a resume or pick one from the vault before analysing.');
      return;
    }
    if (selectedResume.uploadStatus !== 'PARSED') {
      setSubmitError(
        `This resume is in "${selectedResume.uploadStatus}" state and has no usable text to analyse. Upload a text-based PDF or DOCX.`,
      );
      return;
    }

    const trimmedTitle = jobTitle.trim();
    const trimmedDescription = descriptionText.trim();
    const usingSavedJobDescription = selectedJobDescriptionId !== '';

    if (!usingSavedJobDescription) {
      if (trimmedTitle === '') {
        setSubmitError('Job title is required.');
        return;
      }
      if (trimmedDescription === '') {
        setSubmitError('Job description text is required.');
        return;
      }
    }

    let jobDescriptionId: number;
    try {
      if (usingSavedJobDescription) {
        jobDescriptionId = Number(selectedJobDescriptionId);
      } else {
        setStage('creating-jd');
        const created = await jobDescriptionService.create({
          jobTitle: trimmedTitle,
          companyName: companyName.trim() === '' ? null : companyName.trim(),
          descriptionText: trimmedDescription,
        });
        jobDescriptionId = created.id;
        vault.reload();
      }
    } catch (caught) {
      setStage('idle');
      setSubmitError(`Could not save the job description: ${describeError(caught)}`);
      return;
    }

    try {
      setStage('analyzing');
      const analysis = await analysisService.analyze({
        resumeId: selectedResume.id,
        jobDescriptionId,
      });
      navigate(`/results/${analysis.id}`);
    } catch (caught) {
      setStage('idle');
      setSubmitError(
        `The job description was saved, but the analysis failed: ${describeError(caught)}`,
      );
    }
  };

  const busy = uploading || stage !== 'idle';

  return (
    <div className="w-full max-w-7xl mx-auto px-gutter-mobile lg:px-gutter py-space-xl space-y-space-lg">
      {/* Header */}
      <div className="flex flex-col gap-space-xs">
        <div className="flex items-center gap-space-xs font-label-badge text-label-badge text-on-surface-variant">
          <Link className="hover:text-primary transition-colors" to="/analysis/history">
            Analyses
          </Link>
          <span className="material-symbols-outlined text-[14px]">chevron_right</span>
          <span className="text-on-surface font-semibold">New Analysis</span>
        </div>
        <div className="flex flex-col md:flex-row md:items-end justify-between gap-space-md pt-space-xs">
          <div className="space-y-1">
            <h1 className="font-headline-lg-mobile sm:font-headline-lg text-headline-lg-mobile sm:text-headline-lg text-on-surface font-semibold tracking-tight">
              Targeted ATS Resume Analysis
            </h1>
            <p className="font-body-md text-body-md text-on-surface-variant max-w-3xl">
              Upload your resume and paste the target job description to generate an explainable ATS score,
              keyword coverage and skill gap breakdown. Scoring is deterministic — no AI model is involved.
            </p>
          </div>
          <div className="inline-flex items-center gap-space-xs px-space-md py-1.5 bg-surface-container rounded-xl shadow-sm self-start md:self-auto">
            <span className="material-symbols-outlined text-[18px] text-secondary">verified_user</span>
            <span className="font-label-code text-label-code text-on-surface font-medium">
              Deterministic engine • no AI model
            </span>
          </div>
        </div>
      </div>

      {(vault.error || uploadError || submitError) && (
        <ErrorPanel
          message={uploadError ?? submitError ?? vault.error ?? ''}
        />
      )}
      {notice && (
        <div className="flex items-center gap-space-sm p-space-sm rounded-lg bg-secondary-container/40 text-on-secondary-container font-body-sm text-body-sm">
          <span className="material-symbols-outlined text-[18px] text-secondary">check_circle</span>
          <span>{notice}</span>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-lg items-start">
        {/* STEP 1 — resume intake */}
        <div className="lg:col-span-5 flex flex-col gap-space-md">
          <Card className="flex flex-col gap-space-md">
            <div className="flex items-center justify-between pb-space-xs">
              <div className="flex items-center gap-space-xs">
                <span className="flex items-center justify-center w-6 h-6 rounded-full bg-primary-container text-on-primary font-label-badge text-label-badge font-bold">
                  1
                </span>
                <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                  Upload Your Resume
                </h2>
              </div>
              <span className="font-label-code text-label-code text-on-surface-variant">Step 1 of 2</span>
            </div>

            {/* Vault */}
            <div className="space-y-1.5">
              <label
                className="flex items-center justify-between text-body-sm font-body-sm font-medium text-on-surface-variant"
                htmlFor="vault-resume-select"
              >
                <span>Quick Select Vault</span>
                <span className="text-primary font-label-code text-label-code">
                  {vault.loading ? 'Loading…' : `Stored (${resumes.length})`}
                </span>
              </label>
              <div className="relative">
                <select
                  id="vault-resume-select"
                  className="w-full appearance-none bg-surface-container-low text-on-surface text-body-md font-body-md rounded-lg py-2.5 pl-space-md pr-10 focus:outline-none focus:bg-surface-container-lowest transition-all cursor-pointer"
                  value={selectedVaultResumeId}
                  disabled={vault.loading || resumes.length === 0}
                  onChange={(event) => handleVaultSelect(event.target.value)}
                >
                  <option value="">
                    {resumes.length === 0 ? 'No resumes stored yet' : 'Select a previously uploaded resume'}
                  </option>
                  {resumes.map((resume) => (
                    <option key={resume.id} value={String(resume.id)}>
                      {resume.originalFileName} ({formatBytes(resume.fileSize)})
                    </option>
                  ))}
                </select>
                <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-on-surface-variant pointer-events-none text-[20px]">
                  expand_more
                </span>
              </div>
            </div>

            <div className="flex items-center gap-space-sm my-space-xs">
              <div className="h-px bg-surface-container-high flex-1" />
              <span className="font-label-badge text-label-badge text-outline uppercase">Or upload fresh</span>
              <div className="h-px bg-surface-container-high flex-1" />
            </div>

            {/* Dropzone */}
            <div
              className="relative group flex flex-col items-center justify-center p-space-lg rounded-xl bg-surface-container-low hover:bg-surface-container transition-all cursor-pointer text-center"
              onClick={() => !busy && fileInputRef.current?.click()}
              onDragOver={(event) => event.preventDefault()}
              onDrop={(event) => {
                event.preventDefault();
                if (busy) return;
                void handleFileChange(event.dataTransfer.files[0]);
              }}
            >
              <div className="w-12 h-12 rounded-xl bg-surface-container-lowest shadow-sm flex items-center justify-center mb-space-sm group-hover:scale-105 transition-transform text-primary">
                <span className="material-symbols-outlined text-[28px]">
                  {uploading ? 'hourglass_top' : 'cloud_upload'}
                </span>
              </div>
              <p className="font-body-md text-body-md font-medium text-on-surface mb-0.5">
                {uploading ? (
                  'Uploading and parsing…'
                ) : (
                  <>
                    Drag &amp; drop your resume file here or{' '}
                    <span className="text-primary font-semibold underline decoration-primary/30">
                      browse files
                    </span>
                  </>
                )}
              </p>
              <p className="font-label-code text-label-code text-on-surface-variant">
                Supports PDF, DOCX (Max 10MB)
              </p>
              <input
                ref={fileInputRef}
                accept=".pdf,.docx"
                className="absolute inset-0 opacity-0 cursor-pointer"
                type="file"
                disabled={busy}
                // The input sits inside the dropzone, whose onClick opens the
                // picker. Without this the click bubbles back up, the picker
                // opens twice and the file gets uploaded twice.
                onClick={(event) => event.stopPropagation()}
                onChange={(event) => void handleFileChange(event.target.files?.[0])}
              />
            </div>

            {/* Selected resume telemetry — all values come from ResumeResponse */}
            {selectedResume && (
              <div className="p-space-md rounded-xl bg-surface-container-low flex flex-col gap-space-sm">
                <div className="flex items-start justify-between gap-space-sm">
                  <div className="flex items-center gap-space-sm min-w-0">
                    <div className="w-10 h-10 rounded-lg bg-surface-container-high text-primary flex items-center justify-center shrink-0">
                      <span className="material-symbols-outlined text-[24px]">description</span>
                    </div>
                    <div className="min-w-0">
                      <p className="font-body-md text-body-md font-semibold text-on-surface truncate">
                        {selectedResume.originalFileName}
                      </p>
                      <p className="font-label-code text-label-code text-on-surface-variant">
                        {selectedResume.fileType} • {formatBytes(selectedResume.fileSize)} •{' '}
                        {selectedResume.extractedCharacterCount ?? 0} characters extracted
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-1">
                    <button
                      className="p-1.5 rounded-lg text-on-surface-variant hover:text-primary hover:bg-surface-container-high transition-colors"
                      title="Replace file"
                      type="button"
                      disabled={busy}
                      onClick={() => fileInputRef.current?.click()}
                    >
                      <span className="material-symbols-outlined text-[18px]">cached</span>
                    </button>
                    <button
                      className="p-1.5 rounded-lg text-on-surface-variant hover:text-error hover:bg-error-container/30 transition-colors"
                      title="Remove from this analysis (the stored file is kept on the server)"
                      type="button"
                      disabled={busy}
                      onClick={() => {
                        setSelectedResume(null);
                        setSelectedVaultResumeId('');
                        setNotice(null);
                      }}
                    >
                      <span className="material-symbols-outlined text-[18px]">delete</span>
                    </button>
                  </div>
                </div>
                <div className="flex items-center justify-between pt-space-xs mt-1">
                  <span
                    className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-DEFAULT font-label-badge text-label-badge font-semibold ${
                      selectedResume.uploadStatus === 'PARSED'
                        ? 'bg-secondary-container text-on-secondary-container'
                        : 'bg-tertiary-fixed text-tertiary'
                    }`}
                  >
                    <span className="material-symbols-outlined text-[14px]">
                      {selectedResume.uploadStatus === 'PARSED' ? 'check_circle' : 'error'}
                    </span>
                    <span>
                      {selectedResume.uploadStatus === 'PARSED'
                        ? 'Parsed successfully'
                        : `Upload status: ${selectedResume.uploadStatus}`}
                    </span>
                  </span>
                  <span className="font-label-code text-label-code text-secondary font-medium">
                    {selectedResume.uploadStatus === 'PARSED' ? 'Text extracted' : 'No text extracted'}
                  </span>
                </div>
              </div>
            )}

            <div className="p-space-sm rounded-lg bg-surface-container-low flex items-start gap-space-sm text-on-surface-variant font-body-sm text-body-sm">
              <span className="material-symbols-outlined text-primary-container text-[18px] shrink-0 mt-0.5">
                info
              </span>
              <p>
                Text is extracted with PDFBox (PDF) and Apache POI (DOCX). Scanned or image-based files
                contain no text layer and cannot be analysed.
              </p>
            </div>
          </Card>

          {/* Previous results for the selected resume (real data) */}
          {selectedResume && (
            <Card className="flex items-center justify-between gap-space-md">
              <div className="space-y-0.5 min-w-0">
                <SectionLabel>Previous Results For This Resume</SectionLabel>
                <p className="font-headline-sm text-headline-sm font-bold text-on-surface truncate">
                  {selectedResume.originalFileName}
                </p>
                {previousAnalyses === null || previousAnalyses.length === 0 ? (
                  <p className="font-body-sm text-body-sm text-on-surface-variant">
                    No analysis stored for this resume yet.
                  </p>
                ) : (
                  <p className="font-body-sm text-body-sm text-on-surface-variant">
                    {previousAnalyses.length}{' '}
                    {previousAnalyses.length === 1 ? 'stored analysis' : 'stored analyses'} • best{' '}
                    <span className="font-semibold text-secondary">
                      {Math.max(...previousAnalyses.map((item) => item.atsScore))} ATS
                    </span>{' '}
                    • last {formatRelative(previousAnalyses[0].createdAt)}
                  </p>
                )}
              </div>
              {previousAnalyses !== null && previousAnalyses.length > 0 && (
                <div className="w-24 h-10 flex items-end gap-1 px-1 shrink-0" aria-hidden="true">
                  {previousAnalyses
                    .slice(0, 5)
                    .reverse()
                    .map((item) => (
                      <div
                        key={item.id}
                        className="flex-1 bg-primary/70 rounded-t"
                        style={{ height: `${Math.max(8, Math.min(100, item.atsScore))}%` }}
                        title={`Analysis #${item.id}: ${item.atsScore} ATS`}
                      />
                    ))}
                </div>
              )}
            </Card>
          )}
        </div>

        {/* STEP 2 — job description */}
        <div className="lg:col-span-7 flex flex-col gap-space-md">
          <Card className="flex flex-col gap-space-md">
            <div className="flex items-center justify-between pb-space-xs">
              <div className="flex items-center gap-space-xs">
                <span className="flex items-center justify-center w-6 h-6 rounded-full bg-primary-container text-on-primary font-label-badge text-label-badge font-bold">
                  2
                </span>
                <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                  Target Job Details
                </h2>
              </div>
              <span className="font-label-code text-label-code text-on-surface-variant">
                Stored with the analysis
              </span>
            </div>

            {/* Optional reuse of a stored job description */}
            <div className="space-y-1.5">
              <label
                className="flex items-center justify-between text-body-sm font-body-sm font-medium text-on-surface-variant"
                htmlFor="saved-jd-select"
              >
                <span>Reuse a Saved Job Description</span>
                <span className="text-primary font-label-code text-label-code">
                  {jobDescriptions.length} stored
                </span>
              </label>
              <div className="relative">
                <select
                  id="saved-jd-select"
                  className="w-full appearance-none bg-surface-container-low text-on-surface text-body-md font-body-md rounded-lg py-2.5 pl-space-md pr-10 focus:outline-none focus:bg-surface-container-lowest transition-all cursor-pointer"
                  value={selectedJobDescriptionId}
                  disabled={busy || jobDescriptions.length === 0}
                  onChange={(event) => setSelectedJobDescriptionId(event.target.value)}
                >
                  <option value="">
                    {jobDescriptions.length === 0
                      ? 'No job descriptions stored yet'
                      : 'Enter a new job description below'}
                  </option>
                  {jobDescriptions.map((job) => (
                    <option key={job.id} value={String(job.id)}>
                      {job.jobTitle}
                      {job.companyName ? ` — ${job.companyName}` : ''} ({formatDate(job.createdAt)})
                    </option>
                  ))}
                </select>
                <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-on-surface-variant pointer-events-none text-[20px]">
                  expand_more
                </span>
              </div>
            </div>

            {selectedJobDescriptionId !== '' ? (
              <div className="p-space-sm rounded-lg bg-surface-container-low font-body-sm text-body-sm text-on-surface-variant">
                The analysis will use stored job description #{selectedJobDescriptionId}. Clear the selection
                to enter a new one.
              </div>
            ) : (
              <>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-space-md">
                  <div className="space-y-1.5">
                    <label
                      className="flex items-center justify-between text-body-sm font-body-sm font-medium text-on-surface"
                      htmlFor="job-title"
                    >
                      <span>
                        Job Title <span className="text-error">*</span>
                      </span>
                      <span className="font-label-badge text-label-badge text-on-surface-variant">Required</span>
                    </label>
                    <div className="relative">
                      <input
                        id="job-title"
                        className={INPUT_CLASS}
                        placeholder="e.g. Software Engineer (New Grad)"
                        type="text"
                        maxLength={255}
                        value={jobTitle}
                        disabled={busy}
                        onChange={(event) => setJobTitle(event.target.value)}
                      />
                      <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-[18px]">
                        badge
                      </span>
                    </div>
                  </div>
                  <div className="space-y-1.5">
                    <label
                      className="flex items-center justify-between text-body-sm font-body-sm font-medium text-on-surface"
                      htmlFor="company-name"
                    >
                      <span>Company Name</span>
                      <span className="font-label-badge text-label-badge text-on-surface-variant">Optional</span>
                    </label>
                    <div className="relative">
                      <input
                        id="company-name"
                        className={INPUT_CLASS}
                        placeholder="e.g. Acme Corp"
                        type="text"
                        maxLength={255}
                        value={companyName}
                        disabled={busy}
                        onChange={(event) => setCompanyName(event.target.value)}
                      />
                      <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-[18px]">
                        domain
                      </span>
                    </div>
                  </div>
                </div>

                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <label
                      className="text-body-sm font-body-sm font-medium text-on-surface flex items-center gap-1.5"
                      htmlFor="job-description"
                    >
                      <span>
                        Job Description &amp; Role Requirements <span className="text-error">*</span>
                      </span>
                    </label>
                    <div className="flex items-center gap-space-sm font-label-code text-label-code">
                      <button
                        className="inline-flex items-center gap-1 text-primary hover:text-primary-container transition-colors"
                        type="button"
                        disabled={busy}
                        onClick={async () => {
                          try {
                            const text = await navigator.clipboard.readText();
                            if (text) setDescriptionText(text);
                          } catch {
                            setSubmitError('Clipboard access was blocked by the browser. Paste manually.');
                          }
                        }}
                      >
                        <span className="material-symbols-outlined text-[14px]">content_paste</span>
                        <span>Paste clipboard</span>
                      </button>
                      <span className="text-outline-variant">•</span>
                      <button
                        className="inline-flex items-center gap-1 text-on-surface-variant hover:text-error transition-colors"
                        type="button"
                        disabled={busy}
                        onClick={() => setDescriptionText('')}
                      >
                        <span className="material-symbols-outlined text-[14px]">clear</span>
                        <span>Clear</span>
                      </button>
                    </div>
                  </div>
                  <div className="relative">
                    <textarea
                      className={`${INPUT_CLASS} leading-relaxed`}
                      id="job-description"
                      placeholder="Paste the complete job post, responsibilities, and qualifications..."
                      rows={9}
                      maxLength={50000}
                      value={descriptionText}
                      disabled={busy}
                      onChange={(event) => setDescriptionText(event.target.value)}
                    />
                  </div>
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-space-xs font-label-code text-label-code text-on-surface-variant pt-1">
                    <span className="inline-flex items-center gap-1">
                      <span className="w-1.5 h-1.5 rounded-full bg-secondary" />
                      Skills are detected from this text by the curated skill dictionary
                    </span>
                    <span className="text-right">
                      {descriptionText.length.toLocaleString()} / 50,000 characters
                    </span>
                  </div>
                </div>
              </>
            )}

            <div className="pt-space-xs">
              <SectionLabel className="block mb-space-xs">Fixed Scoring Weights</SectionLabel>
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-space-sm">
                {SCORING_WEIGHTS.map((item) => (
                  <div
                    key={item.label}
                    className="flex items-center gap-space-xs p-2.5 rounded-lg bg-surface-container-low"
                  >
                    <span className="font-label-code text-label-code text-primary font-bold">{item.weight}%</span>
                    <span className="font-body-sm text-body-sm text-on-surface font-medium">{item.label}</span>
                  </div>
                ))}
              </div>
            </div>
          </Card>
        </div>
      </div>

      {/* Action bar */}
      <div className="w-full bg-surface-container-lowest p-space-md md:px-space-lg rounded-xl shadow-md flex flex-col md:flex-row items-center justify-between gap-space-md sticky bottom-4 z-40">
        <div className="flex items-center gap-space-sm text-on-surface-variant font-body-sm text-body-sm">
          <div className="w-8 h-8 rounded-full bg-secondary-container flex items-center justify-center text-on-secondary-container">
            <span className="material-symbols-outlined text-[18px]">neurology</span>
          </div>
          <div>
            <p className="font-semibold text-on-surface leading-tight">
              {stage === 'creating-jd'
                ? 'Saving the job description…'
                : stage === 'analyzing'
                  ? 'Analysing resume against the job description…'
                  : 'Analysis runs as a single backend request'}
            </p>
            <p className="font-label-code text-label-code text-on-surface-variant">
              Deterministic engine • progress is indeterminate (the backend reports no percentage)
            </p>
          </div>
        </div>
        <div className="flex items-center gap-space-sm w-full md:w-auto justify-end">
          <button
            className="px-space-md py-2.5 rounded-lg bg-surface-container-low text-on-surface-variant font-body-md text-body-md font-medium transition-colors shadow-sm cursor-not-allowed opacity-60"
            type="button"
            disabled
            title="Drafts are not supported by the current backend"
          >
            Save Draft
          </button>
          <button
            className="relative group inline-flex items-center justify-center gap-space-xs px-space-xl py-2.5 rounded-lg bg-primary-container text-on-primary font-body-md text-body-md font-semibold hover:bg-primary transition-all shadow-md shadow-primary/20 overflow-hidden disabled:opacity-60 disabled:cursor-not-allowed"
            id="trigger-analyze"
            type="button"
            disabled={busy}
            onClick={() => void handleSubmit()}
          >
            {busy ? (
              <>
                <span className="material-symbols-outlined text-[20px] animate-spin">refresh</span>
                <span>{stage === 'analyzing' ? 'Analysing…' : 'Working…'}</span>
              </>
            ) : (
              <>
                <span className="material-symbols-outlined text-[20px] transition-transform group-hover:rotate-45">
                  analytics
                </span>
                <span>Analyze Resume &amp; Match Job →</span>
              </>
            )}
          </button>
        </div>
      </div>

      {/* Honest footnote checklist */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-space-md pt-space-xs">
        <div className="flex items-start gap-space-sm p-space-sm">
          <span className="material-symbols-outlined text-secondary text-[20px]">check_circle</span>
          <div>
            <p className="font-body-md text-body-md font-semibold text-on-surface">Deterministic Scoring</p>
            <p className="font-body-sm text-body-sm text-on-surface-variant">
              Every number on the results page is produced by the backend engine and stored with the analysis.
            </p>
          </div>
        </div>
        <div className="flex items-start gap-space-sm p-space-sm">
          <span className="material-symbols-outlined text-primary text-[20px]">database</span>
          <div>
            <p className="font-body-md text-body-md font-semibold text-on-surface">Files Are Stored Server-Side</p>
            <p className="font-body-sm text-body-sm text-on-surface-variant">
              Uploaded resumes are written to the configured storage directory and recorded in the database.
            </p>
          </div>
        </div>
        <div className="flex items-start gap-space-sm p-space-sm">
          <span className="material-symbols-outlined text-surface-tint text-[20px]">block</span>
          <div>
            <p className="font-body-md text-body-md font-semibold text-on-surface">No AI Generation</p>
            <p className="font-body-sm text-body-sm text-on-surface-variant">
              There is no AI service in this project, so no rewritten suggestions or auto-edits are offered.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
