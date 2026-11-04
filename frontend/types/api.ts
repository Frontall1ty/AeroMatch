/** Error codes the backend may return in the `errorCode` field. */
export type ApiErrorCode =
  | "UNAUTHENTICATED"
  | "SESSION_EXPIRED"
  | "FORBIDDEN"
  | "CSRF_INVALID"
  | "UNKNOWN_ERROR"
  // The backend may add codes; keep the union open for unknown strings.
  | (string & {});

export type ApiError = {
  errorCode: ApiErrorCode;
  message: string;
};

/** Response of GET /api/v1/auth/csrf. */
export type CsrfResponse = {
  headerName: string;
  token: string;
};
