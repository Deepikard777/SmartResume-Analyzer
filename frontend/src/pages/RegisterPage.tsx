import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ErrorPanel } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { describeError } from '../services/api';

const INPUT_CLASS =
  'w-full bg-surface-container-low text-on-surface font-body-md text-body-md rounded-lg py-2.5 px-space-md focus:bg-surface-container-lowest focus:ring-2 focus:ring-primary/20 focus:outline-none transition-all';

/**
 * Registration screen.
 * Field rules mirror RegisterRequest: name 2–100 chars, valid email, password min 6.
 */
export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);

    if (name.trim().length < 2) {
      setError('Please enter your name (at least 2 characters).');
      return;
    }
    if (password.length < 6) {
      setError('Please choose a password with at least 6 characters.');
      return;
    }
    if (password !== confirmPassword) {
      setError('The two passwords do not match.');
      return;
    }

    setSubmitting(true);
    try {
      await register(name.trim(), email.trim(), password);
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
            Create your account
          </h1>
          <p className="font-body-md text-body-md text-on-surface-variant max-w-sm">
            Register to run ATS analyses and keep a history of your results.
          </p>
        </div>

        <form
          onSubmit={handleSubmit}
          className="rounded-xl bg-surface-container-lowest shadow-sm p-space-lg flex flex-col gap-space-md"
        >
          {error && <ErrorPanel message={error} />}

          <div className="space-y-1.5">
            <label className="text-body-sm font-body-sm font-medium text-on-surface" htmlFor="register-name">
              Full name
            </label>
            <input
              id="register-name"
              type="text"
              autoComplete="name"
              required
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="Jane Doe"
              className={INPUT_CLASS}
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-body-sm font-body-sm font-medium text-on-surface" htmlFor="register-email">
              Email
            </label>
            <input
              id="register-email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@example.com"
              className={INPUT_CLASS}
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-space-md">
            <div className="space-y-1.5">
              <label
                className="text-body-sm font-body-sm font-medium text-on-surface"
                htmlFor="register-password"
              >
                Password
              </label>
              <input
                id="register-password"
                type="password"
                autoComplete="new-password"
                required
                minLength={6}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="Min. 6 characters"
                className={INPUT_CLASS}
              />
            </div>
            <div className="space-y-1.5">
              <label
                className="text-body-sm font-body-sm font-medium text-on-surface"
                htmlFor="register-confirm"
              >
                Confirm password
              </label>
              <input
                id="register-confirm"
                type="password"
                autoComplete="new-password"
                required
                minLength={6}
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                placeholder="Repeat password"
                className={INPUT_CLASS}
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="inline-flex items-center justify-center gap-space-xs px-space-lg py-2.5 rounded-lg bg-primary-container text-on-primary font-body-md text-body-md font-semibold hover:bg-primary transition-colors shadow-md shadow-primary/20 disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {submitting ? (
              <>
                <span className="material-symbols-outlined text-[20px] animate-spin">refresh</span>
                <span>Creating account…</span>
              </>
            ) : (
              <>
                <span className="material-symbols-outlined text-[20px]">person_add</span>
                <span>Create account</span>
              </>
            )}
          </button>

          <p className="font-body-sm text-body-sm text-on-surface-variant text-center">
            Already registered?{' '}
            <Link to="/login" className="text-primary font-semibold hover:underline">
              Sign in
            </Link>
          </p>
        </form>
      </div>
    </div>
  );
}
