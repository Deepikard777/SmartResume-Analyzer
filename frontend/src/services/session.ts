/**
 * The single place the bearer token lives on the client.
 *
 * Only the token is stored — never a password, and never anything derived from one.
 * Keeping it in its own module (instead of the React context) means the low-level
 * fetch wrapper can read it without importing React, so there is no circular
 * dependency between AuthContext and the API layer.
 */

const TOKEN_KEY = 'smartresume.token';
const USER_KEY = 'smartresume.currentUser';

type UnauthorizedHandler = () => void;

let unauthorizedHandler: UnauthorizedHandler | null = null;

/**
 * Registered by AuthContext. Called when the backend answers 401 to a request
 * that carried a token, i.e. the session is no longer valid.
 */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler;
}

export function notifyUnauthorized(): void {
  if (unauthorizedHandler) {
    unauthorizedHandler();
  }
}

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string): void {
  try {
    localStorage.setItem(TOKEN_KEY, token);
  } catch {
    // Private-mode storage failures must not break the sign-in flow.
  }
}

export function getStoredUserJson(): string | null {
  try {
    return localStorage.getItem(USER_KEY);
  } catch {
    return null;
  }
}

export function setStoredUserJson(serialised: string): void {
  try {
    localStorage.setItem(USER_KEY, serialised);
  } catch {
    // Ignore.
  }
}

/** Remove both the token and the cached user. */
export function clearSession(): void {
  try {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  } catch {
    // Ignore.
  }
}
