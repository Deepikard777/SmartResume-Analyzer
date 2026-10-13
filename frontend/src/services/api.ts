import type { ApiErrorBody } from '../types/api';
import { friendlyMessage } from '../utils/http';
import { getToken, notifyUnauthorized } from './session';

/**
 * Single place where the backend base URL is resolved.
 * Defaults to localhost:8080 (Spring Boot default) and can be overridden
 * through VITE_API_BASE_URL in .env.
 */
export const API_BASE_URL: string =
  (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/+$/, '') ??
  'http://localhost:8080';

/** Normalised error thrown by every service call. */
export class ApiError extends Error {
  readonly status: number;
  readonly serverMessage: string | null;
  readonly validationErrors: Record<string, string> | null;

  constructor(
    status: number,
    userMessage: string,
    serverMessage: string | null = null,
    validationErrors: Record<string, string> | null = null,
  ) {
    super(userMessage);
    this.name = 'ApiError';
    this.status = status;
    this.serverMessage = serverMessage;
    this.validationErrors = validationErrors;
  }
}

async function parseErrorBody(response: Response): Promise<ApiErrorBody | null> {
  try {
    const text = await response.text();
    if (!text) return null;
    return JSON.parse(text) as ApiErrorBody;
  } catch {
    return null;
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  /** JSON body. Mutually exclusive with `formData`. */
  body?: unknown;
  /** multipart/form-data body. Content-Type is left to the browser. */
  formData?: FormData;
}

/**
 * The only fetch call in the application.
 * Handles JSON encoding/decoding, backend error envelopes, non-JSON responses
 * and network failure (which surfaces as status 0).
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, formData } = options;
  const headers: Record<string, string> = { Accept: 'application/json' };

  // Attach the bearer token when one is held. This is the only place a token is
  // added to a request, so no component can forget it.
  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  let payload: BodyInit | undefined;
  if (formData) {
    payload = formData;
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { method, headers, body: payload });
  } catch {
    throw new ApiError(0, 'Cannot reach the backend. Make sure the Spring Boot server is running on ' + API_BASE_URL + '.');
  }

  if (response.status === 204) {
    return undefined as T;
  }

  if (!response.ok) {
    // A 401 on a request that carried a token means the session is no longer valid
    // (expired, tampered, or the account was removed). Tell the auth layer so it can
    // clear the session and let the route guard send the user to the login screen.
    // A 401 without a token is a failed sign-in attempt and must NOT do this.
    if (response.status === 401 && token) {
      notifyUnauthorized();
    }

    const errorBody = await parseErrorBody(response);
    const base = friendlyMessage(response.status);
    throw new ApiError(
      response.status,
      base,
      errorBody?.message ?? null,
      errorBody?.validationErrors ?? null,
    );
  }

  const text = await response.text();
  if (!text) {
    return undefined as T;
  }

  try {
    return JSON.parse(text) as T;
  } catch {
    throw new ApiError(response.status, 'The server returned a response that could not be read.');
  }
}

/** A binary response plus the headers needed to name the downloaded file. */
export interface BlobResponse {
  blob: Blob;
  contentDisposition: string | null;
}

/**
 * Fetch a binary body (the PDF report) with the same auth and error handling as
 * `request`. A plain <a href> cannot be used: the endpoint requires an Authorization
 * header, which only a fetch can add.
 */
export async function requestBlob(path: string, options: RequestOptions = {}): Promise<BlobResponse> {
  const { method = 'GET' } = options;
  const headers: Record<string, string> = { Accept: 'application/pdf, */*' };

  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { method, headers });
  } catch {
    throw new ApiError(
      0,
      'Cannot reach the backend. Make sure the Spring Boot server is running on ' + API_BASE_URL + '.',
    );
  }

  if (!response.ok) {
    if (response.status === 401 && token) {
      notifyUnauthorized();
    }
    const errorBody = await parseErrorBody(response);
    throw new ApiError(
      response.status,
      friendlyMessage(response.status),
      errorBody?.message ?? null,
      errorBody?.validationErrors ?? null,
    );
  }

  return {
    blob: await response.blob(),
    contentDisposition: response.headers.get('Content-Disposition'),
  };
}

/**
 * Hand a blob to the browser as a download.
 * Creates a temporary object URL and revokes it, so nothing is left behind.
 */
export function saveBlobAsFile(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  anchor.rel = 'noopener';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  // Revoke on the next tick so the download has started.
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

/** Human-readable text for any thrown value. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    const detail = error.serverMessage;
    if (detail && detail !== error.message) {
      return `${error.message} (${detail})`;
    }
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return 'An unexpected error occurred.';
}
