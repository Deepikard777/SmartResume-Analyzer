import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { LoadingPanel } from './ui';
import { useAuth } from '../context/AuthContext';

/**
 * Route guard.
 *
 * The backend is now the enforcement point: every data endpoint rejects a request
 * without a valid JWT (401). This guard is the matching client-side behaviour — it
 * waits for the stored token to be re-validated, then redirects to /login when there
 * is no valid session, so protected pages never render with missing data.
 */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user, token, initializing } = useAuth();

  if (initializing) {
    return (
      <div className="min-h-screen bg-surface flex items-center justify-center px-gutter-mobile">
        <LoadingPanel label="Restoring your session…" />
      </div>
    );
  }

  if (!user || !token) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}
