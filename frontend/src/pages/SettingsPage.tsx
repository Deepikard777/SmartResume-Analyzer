import { useState } from 'react';
import { Badge, Card, SectionLabel } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { API_BASE_URL, describeError } from '../services/api';
import { resumeService } from '../services/resumeService';

const INTEGRATED_ENDPOINTS: { method: string; path: string; purpose: string }[] = [
  { method: 'POST', path: '/api/auth/register', purpose: 'Create an account (returns a JWT)' },
  { method: 'POST', path: '/api/auth/login', purpose: 'Sign in and obtain a JWT' },
  { method: 'GET', path: '/api/auth/me', purpose: 'Validate the stored token and return the account' },
  { method: 'POST', path: '/api/resumes/upload', purpose: 'Upload and parse a PDF/DOCX resume' },
  { method: 'GET', path: '/api/resumes', purpose: 'List stored resumes' },
  { method: 'GET', path: '/api/resumes/{id}', purpose: 'Resume detail including extracted text' },
  { method: 'DELETE', path: '/api/resumes/{id}', purpose: 'Delete a resume' },
  { method: 'POST', path: '/api/job-descriptions', purpose: 'Create a job description' },
  { method: 'GET', path: '/api/job-descriptions', purpose: 'List job descriptions' },
  { method: 'GET', path: '/api/job-descriptions/{id}', purpose: 'Job description detail' },
  { method: 'DELETE', path: '/api/job-descriptions/{id}', purpose: 'Delete a job description' },
  { method: 'POST', path: '/api/analyses', purpose: 'Run and persist an ATS analysis' },
  { method: 'GET', path: '/api/analyses', purpose: 'Analysis history' },
  { method: 'GET', path: '/api/analyses/{id}', purpose: 'Stored analysis result' },
];

const LIMITATIONS: { title: string; detail: string }[] = [
  {
    title: 'No refresh tokens and no revocation list',
    detail:
      'The JWT is valid until it expires and cannot be revoked server-side, so signing out only discards the token on this device. A refresh-token store or a deny-list is the usual next step.',
  },
  {
    title: 'The signing key must be supplied for stable sessions',
    detail:
      'The secret comes from the JWT_SECRET environment variable and is never hardcoded. If it is unset, the server generates a random key at startup: that is secure, but every session ends when the process restarts.',
  },
  {
    title: 'No password reset, email verification or role model',
    detail:
      'There is a single user role, no email delivery and no password-recovery flow. Registration accepts any well-formed address that is not already taken.',
  },
  {
    title: 'Recommendations are deterministic, not AI-generated',
    detail:
      'There is no LLM client and no auto-patch feature. Every recommendation on the results screen is derived from the measured findings — matched and missing skills, keywords, structure warnings, repetition and sub-scores — and never invents resume content.',
  },
  {
    title: 'Scoring is a project-defined heuristic',
    detail:
      'The ATS figure is a fixed weighted blend of the five sub-scores. It does not reproduce any commercial ATS product, and the engine source says so explicitly.',
  },
  {
    title: 'No aggregate dashboard endpoint',
    detail:
      'Dashboard totals, averages and the missing-skill matrix are derived in the frontend from GET /api/resumes and GET /api/analyses rather than from a dedicated backend endpoint.',
  },
  {
    title: 'No PDF export, drafts or per-keyword frequency in the UI',
    detail:
      'Those actions have no backend support, so the corresponding controls are disabled or clearly labelled rather than faked. Per-keyword frequencies are stored but are not part of the analysis response contract.',
  },
];

/**
 * Settings / profile.
 *
 * Stitch provides no settings design (only a nav link), so this follows the
 * existing card and token conventions. Every value shown is real: the signed-in
 * user object, the configured API base URL, and a live connectivity probe.
 */
export function SettingsPage() {
  const { user, token, logout } = useAuth();
  const [checking, setChecking] = useState(false);
  const [checkResult, setCheckResult] = useState<{ ok: boolean; message: string } | null>(null);

  const runConnectivityCheck = async () => {
    setChecking(true);
    setCheckResult(null);
    try {
      const resumes = await resumeService.list();
      setCheckResult({
        ok: true,
        message: `Backend reachable. GET /api/resumes returned ${resumes.length} ${
          resumes.length === 1 ? 'resume' : 'resumes'
        }.`,
      });
    } catch (caught) {
      setCheckResult({ ok: false, message: describeError(caught) });
    } finally {
      setChecking(false);
    }
  };

  return (
    <div className="w-full max-w-7xl mx-auto px-gutter-mobile lg:px-gutter py-space-xl flex flex-col gap-space-lg">
      <div className="flex flex-col gap-space-xs">
        <SectionLabel>Settings</SectionLabel>
        <h1 className="font-headline-lg-mobile sm:font-headline-lg text-headline-lg-mobile sm:text-headline-lg text-on-surface font-semibold tracking-tight">
          Account &amp; Application
        </h1>
        <p className="font-body-md text-body-md text-on-surface-variant max-w-3xl">
          What this build actually knows about you, which backend endpoints it calls, and which capabilities
          do not exist yet.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-lg items-start">
        <div className="lg:col-span-5 flex flex-col gap-space-lg">
          <Card className="flex flex-col gap-space-md">
            <div className="flex items-center gap-space-sm">
              <div className="w-12 h-12 rounded-full bg-primary flex items-center justify-center">
                <span className="material-symbols-outlined text-on-primary text-[24px]">person</span>
              </div>
              <div className="flex flex-col min-w-0">
                <span className="font-headline-sm text-headline-sm font-semibold text-on-surface truncate">
                  {user?.name ?? 'Unknown user'}
                </span>
                <span className="font-body-sm text-body-sm text-on-surface-variant truncate">
                  {user?.email ?? '—'}
                </span>
              </div>
            </div>

            <div className="flex flex-col gap-space-xs">
              <div className="flex items-center justify-between p-space-sm rounded-lg bg-surface-container-low">
                <span className="font-label-code text-label-code text-on-surface-variant">User ID</span>
                <span className="font-label-code text-label-code text-on-surface font-semibold">
                  {user?.id ?? '—'}
                </span>
              </div>
              <div className="flex items-center justify-between p-space-sm rounded-lg bg-surface-container-low">
                <span className="font-label-code text-label-code text-on-surface-variant">Auth token</span>
                {token ? (
                  <Badge tone="success">Issued · HS256 bearer</Badge>
                ) : (
                  <Badge tone="warning">None</Badge>
                )}
              </div>
            </div>

            <button
              type="button"
              onClick={logout}
              className="inline-flex items-center justify-center gap-space-xs px-space-md py-2 rounded-lg bg-surface-container text-on-surface font-body-md text-body-md font-medium hover:bg-surface-container-high transition-colors"
            >
              <span className="material-symbols-outlined text-[18px]">logout</span>
              <span>Sign out</span>
            </button>
          </Card>

          <Card className="flex flex-col gap-space-md">
            <div className="flex flex-col">
              <SectionLabel>Connection</SectionLabel>
              <h2 className="font-headline-sm text-headline-sm font-semibold text-on-surface">
                Backend API
              </h2>
            </div>
            <div className="flex items-center justify-between p-space-sm rounded-lg bg-surface-container-low gap-space-sm">
              <span className="font-label-code text-label-code text-on-surface-variant">Base URL</span>
              <span className="font-label-code text-label-code text-on-surface font-semibold truncate">
                {API_BASE_URL}
              </span>
            </div>
            <button
              type="button"
              onClick={() => void runConnectivityCheck()}
              disabled={checking}
              className="inline-flex items-center justify-center gap-space-xs px-space-md py-2 rounded-lg bg-primary-container text-on-primary font-body-md text-body-md font-medium hover:bg-primary transition-colors disabled:opacity-60"
            >
              <span className={`material-symbols-outlined text-[18px] ${checking ? 'animate-spin' : ''}`}>
                {checking ? 'refresh' : 'network_check'}
              </span>
              <span>{checking ? 'Checking…' : 'Test connection'}</span>
            </button>
            {checkResult && (
              <div
                className={`p-space-sm rounded-lg font-body-sm text-body-sm ${
                  checkResult.ok
                    ? 'bg-secondary-container/40 text-on-secondary-container'
                    : 'bg-tertiary-fixed/40 text-tertiary'
                }`}
              >
                {checkResult.message}
              </div>
            )}
          </Card>
        </div>

        <div className="lg:col-span-7 flex flex-col gap-space-lg">
          <Card className="flex flex-col gap-space-md">
            <div className="flex flex-col">
              <SectionLabel>Integration Surface</SectionLabel>
              <h2 className="font-headline-sm text-headline-sm font-semibold text-on-surface">
                Endpoints Used By This UI
              </h2>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full border-collapse">
                <tbody>
                  {INTEGRATED_ENDPOINTS.map((endpoint) => (
                    <tr
                      key={`${endpoint.method}-${endpoint.path}`}
                      className="border-b border-outline-variant/20 last:border-b-0"
                    >
                      <td className="py-space-sm pr-space-md">
                        <span className="font-label-badge text-label-badge font-semibold text-primary">
                          {endpoint.method}
                        </span>
                      </td>
                      <td className="py-space-sm pr-space-md">
                        <span className="font-label-code text-label-code text-on-surface">
                          {endpoint.path}
                        </span>
                      </td>
                      <td className="py-space-sm">
                        <span className="font-body-sm text-body-sm text-on-surface-variant">
                          {endpoint.purpose}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>

          <Card className="flex flex-col gap-space-md">
            <div className="flex flex-col">
              <SectionLabel>Honest Limitations</SectionLabel>
              <h2 className="font-headline-sm text-headline-sm font-semibold text-on-surface">
                What This Build Does Not Do
              </h2>
            </div>
            <div className="flex flex-col gap-space-sm">
              {LIMITATIONS.map((limitation) => (
                <div
                  key={limitation.title}
                  className="p-space-sm rounded-lg bg-surface-container-low flex items-start gap-space-sm"
                >
                  <span className="material-symbols-outlined text-tertiary text-[18px] mt-0.5 shrink-0">
                    info
                  </span>
                  <div className="flex flex-col">
                    <span className="font-body-md text-body-md font-semibold text-on-surface">
                      {limitation.title}
                    </span>
                    <p className="font-body-sm text-body-sm text-on-surface-variant mt-0.5">
                      {limitation.detail}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
}
