import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { ErrorPanel, LoadingPanel } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { describeError } from '../services/api';

const INPUT_CLASS =
  'w-full bg-surface-container-low text-on-surface font-body-md text-body-md rounded-lg py-2.5 px-space-md focus:bg-surface-container-lowest focus:ring-2 focus:ring-primary/20 focus:outline-none transition-all';

/**
 * Sign-in screen.
 *
 * Stitch ships no authentication screen, so this is authored with the project's
 * own design tokens (see DESIGN.md) rather than invented styling.
 */
export function LoginPage() {
  const { login, user, initializing } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Wait for a stored token to be re-validated so an already signed-in user is not
  // shown the form for a moment before being redirected.
  if (initializing) {
    return (
      <div className="min-h-screen bg-surface flex items-center justify-center px-gutter-mobile">
        <LoadingPanel label="Restoring your session…" />
      </div>
    );
  }

  if (user) {
    return <Navigate to="/" replace />;
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      navigate('/', { replace: true });
    } catch (caught) {
      setError(describeError(caught));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-surface font-body-md text-on-surface antialiased flex flex-col items-center justify-center px-gutter-mobile py-space-xl">
      <div className="w-full max-w-md flex flex-col gap-space-lg">
        <div className="flex flex-col items-center gap-space-sm text-center">
          <img alt="SmartResume Analyzer Logo" className="h-12 w-12" src="/logo.svg" />
          <h1 className="font-headline-lg-mobile sm:font-headline-lg text-headline-lg-mobile sm:text-headline-lg text-on-surface tracking-tight">
            Sign in to SmartResume Analyzer
          </h1>
          <p className="font-body-md text-body-md text-on-surface-variant max-w-sm">
            Continue to targeted ATS analysis, skill gap diagnostics and saved analysis history.
          </p>
        </div>

        <form
          onSubmit={handleSubmit}
          className="rounded-xl bg-surface-container-lowest shadow-sm p-space-lg flex flex-col gap-space-md"
        >
          {error && <ErrorPanel message={error} />}

          <div className="space-y-1.5">
            <label className="text-body-sm font-body-sm font-medium text-on-surface" htmlFor="login-email">
              Email
            </label>
            <input
              id="login-email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@example.com"
              className={INPUT_CLASS}
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-body-sm font-body-sm font-medium text-on-surface" htmlFor="login-password">
              Password
            </label>
            <input
              id="login-password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="••••••••"
              className={INPUT_CLASS}
            />
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="inline-flex items-center justify-center gap-space-xs px-space-lg py-2.5 rounded-lg bg-primary-container text-on-primary font-body-md text-body-md font-semibold hover:bg-primary transition-colors shadow-md shadow-primary/20 disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {submitting ? (
              <>
                <span className="material-symbols-outlined text-[20px] animate-spin">refresh</span>
                <span>Signing in…</span>
              </>
            ) : (
              <>
                <span className="material-symbols-outlined text-[20px]">login</span>
                <span>Sign in</span>
              </>
            )}
          </button>

          <p className="font-body-sm text-body-sm text-on-surface-variant text-center">
            No account yet?{' '}
            <Link to="/register" className="text-primary font-semibold hover:underline">
              Create one
            </Link>
          </p>
        </form>

        <div className="p-space-sm rounded-lg bg-surface-container-low flex items-start gap-space-sm text-on-surface-variant font-body-sm text-body-sm">
          <span className="material-symbols-outlined text-primary-container text-[18px] shrink-0 mt-0.5">
            info
          </span>
          <p>
            Credentials are checked by
            <span className="font-label-code text-label-code"> POST /api/auth/login </span>
            against BCrypt-hashed passwords, and a signed JWT is returned. That token is sent as
            <span className="font-label-code text-label-code"> Authorization: Bearer &lt;token&gt; </span>
            on every request, and every resume, job description and analysis is scoped to your account.
            Only the token is stored in the browser — never your password.
          </p>
        </div>
      </div>
    </div>
  );
}
