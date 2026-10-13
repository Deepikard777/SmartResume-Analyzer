import { request } from './api';
import type { AuthResponse, User } from '../types/api';

/**
 * Auth endpoints.
 *
 * POST /api/auth/register and POST /api/auth/login both return a signed JWT
 * (`token`) alongside the public user object. The client sends that token as
 * `Authorization: Bearer <token>` on every subsequent request — see services/api.ts
 * and services/session.ts. The password is never stored on the client.
 */
export const authService = {
  register(name: string, email: string, password: string): Promise<AuthResponse> {
    return request<AuthResponse>('/api/auth/register', {
      method: 'POST',
      body: { name, email, password },
    });
  },

  login(email: string, password: string): Promise<AuthResponse> {
    return request<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: { email, password },
    });
  },

  /**
   * GET /api/auth/me — the account behind the stored token.
   * Used on page load to confirm the session is still valid instead of trusting
   * whatever happens to be in localStorage.
   */
  me(): Promise<User> {
    return request<User>('/api/auth/me');
  },
};

export type { User };
