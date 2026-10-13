import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { authService } from '../services/authService';
import {
  clearSession,
  getStoredUserJson,
  getToken,
  setStoredUserJson,
  setToken,
  setUnauthorizedHandler,
} from '../services/session';
import type { User } from '../types/api';

/**
 * Real, token-based session state.
 *
 * The backend issues a signed JWT on register/login. That token is stored (and
 * nothing else — never a password) and is attached to every API request by
 * services/api.ts. On page load the token is re-validated against
 * GET /api/auth/me, so a stale or tampered token is discarded instead of being
 * trusted. A 401 on any request clears the session.
 */
interface AuthState {
  user: User | null;
  token: string | null;
  /** True while a stored token is being validated on first render. */
  initializing: boolean;
  login: (email: string, password: string) => Promise<User>;
  register: (name: string, email: string, password: string) => Promise<User>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

function readStoredUser(): User | null {
  const raw = getStoredUserJson();
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<User>;
    if (
      typeof parsed.id === 'number' &&
      typeof parsed.email === 'string' &&
      typeof parsed.name === 'string'
    ) {
      return { id: parsed.id, name: parsed.name, email: parsed.email };
    }
    return null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => readStoredUser());
  const [token, setTokenState] = useState<string | null>(() => getToken());
  const [initializing, setInitializing] = useState<boolean>(() => getToken() !== null);

  const applySession = useCallback((nextUser: User, nextToken: string) => {
    setUser(nextUser);
    setTokenState(nextToken);
    setToken(nextToken);
    // Only the public user object is cached — never credentials.
    setStoredUserJson(JSON.stringify(nextUser));
  }, []);

  const logout = useCallback(() => {
    setUser(null);
    setTokenState(null);
    clearSession();
  }, []);

  // Any 401 on a token-carrying request invalidates the session.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      setUser(null);
      setTokenState(null);
      clearSession();
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  // Validate a stored token once on mount.
  useEffect(() => {
    const stored = getToken();
    if (!stored) {
      setInitializing(false);
      return;
    }

    let cancelled = false;
    authService
      .me()
      .then((freshUser) => {
        if (cancelled) return;
        setUser(freshUser);
        setStoredUserJson(JSON.stringify(freshUser));
      })
      .catch(() => {
        if (cancelled) return;
        // Expired, tampered or account removed: drop the session and sign out.
        setUser(null);
        setTokenState(null);
        clearSession();
      })
      .finally(() => {
        if (!cancelled) setInitializing(false);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      const response = await authService.login(email, password);
      applySession(response.user, response.token);
      return response.user;
    },
    [applySession],
  );

  const register = useCallback(
    async (name: string, email: string, password: string) => {
      const response = await authService.register(name, email, password);
      applySession(response.user, response.token);
      return response.user;
    },
    [applySession],
  );

  const value = useMemo<AuthState>(
    () => ({ user, token, initializing, login, register, logout }),
    [user, token, initializing, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider');
  }
  return context;
}
