import type { ApiError } from "@/types/api";

export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export const API_V1_PATH = "/api/v1";

/** Builds an absolute URL for a versioned API path, e.g. apiV1("/quotes"). */
export function apiV1(path: string): string {
  return `${API_BASE_URL}${API_V1_PATH}${path.startsWith("/") ? path : `/${path}`}`;
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

/** Minimal typed fetch wrapper. `url` must be absolute (use apiV1). */
export async function apiFetch<T>(
  url: string,
  init?: RequestInit,
): Promise<T> {
  const response = await fetch(url, {
    ...init,
    headers: { Accept: "application/json", ...init?.headers },
  });
  if (!response.ok) {
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
    throw new ApiRequestError(response.status, error);
  }
  return (await response.json()) as T;
}
