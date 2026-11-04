package com.aeromatch.common.exception;

/** Standard error body: machine-readable code plus user-facing message (HTTP status is on the response). */
public record ApiError(String errorCode, String message) {}
