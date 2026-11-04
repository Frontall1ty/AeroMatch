import type { ApiError, CsrfResponse } from "@/types/api";

/**
 * All API calls use relative URLs. Production is single-origin behind the
 * reverse proxy (Caddy routes /api/* to the backend); in development Next.js
 * rewrites proxy /api/* to the local backend (see next.config.ts).
 * Authentication is a server-side HttpOnly session cookie; no tokens are
 * ever stored in localStorage/sessionStorage.
 */
export const API_V1_PATH = "/api/v1";
export const CSRF_PATH = "/auth/csrf";

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

/** Builds a relative URL for a versioned API path, e.g. apiV1("/quotes"). */
export function apiV1(path: string): string {
  return `${API_V1_PATH}${path.startsWith("/") ? path : `/${path}`}`;
}

export class ApiRequestError extends Error {
  readonly status: number;
  readonly error: ApiError;

  constructor(status: number, error: ApiError) {
    super(error.message);
    this.name = "ApiRequestError";
    this.status = status;
    this.error = error;
  }
}

// In-memory only (module scope). Never persisted to web storage.
let csrf: CsrfResponse | null = null;

/** Clears the cached CSRF token (e.g. after logout). */
export function resetCsrfToken(): void {
  csrf = null;
}

async function toApiError(response: Response): Promise<ApiRequestError> {
  let error: ApiError = {
    errorCode: "UNKNOWN_ERROR",
    message: `Request failed with status ${response.status}`,
  };
  try {
    const body = (await response.json()) as Partial<ApiError>;
    if (body.errorCode && body.message) {
      error = { errorCode: body.errorCode, message: body.message };
    }
  } catch {
    // non-JSON error body; keep the fallback
  }
  return new ApiRequestError(response.status, error);
}

async function fetchCsrf(): Promise<CsrfResponse> {
  const response = await fetch(apiV1(CSRF_PATH), {
    credentials: "same-origin",
    headers: { Accept: "application/json" },
  });
  if (!response.ok) {
    throw await toApiError(response);
  }
  csrf = (await response.json()) as CsrfResponse;
  return csrf;
}

function send(
  path: string,
  init: RequestInit | undefined,
  token: CsrfResponse | null,
): Promise<Response> {
  const headers = new Headers(init?.headers);
  if (!headers.has("Accept")) headers.set("Accept", "application/json");
  if (token) headers.set(token.headerName, token.token);
  return fetch(apiV1(path), { ...init, headers, credentials: "same-origin" });
}

/**
 * Typed fetch wrapper for the versioned API. `path` is relative to /api/v1.
 * Unsafe methods carry the CSRF header; on 403 CSRF_INVALID the token is
 * refreshed once and the request retried once.
 */
export async function apiFetch<T>(
  path: string,
  init?: RequestInit,
): Promise<T> {
  const method = (init?.method ?? "GET").toUpperCase();
  const unsafe = !SAFE_METHODS.has(method);

  const token = unsafe ? (csrf ?? (await fetchCsrf())) : null;
  let response = await send(path, init, token);

  if (unsafe && response.status === 403) {
    const err = await toApiError(response.clone());
    if (err.error.errorCode === "CSRF_INVALID") {
      response = await send(path, init, await fetchCsrf());
    }
  }

  if (!response.ok) {
    throw await toApiError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}
