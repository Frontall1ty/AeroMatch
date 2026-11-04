package com.aeromatch.common.security;

import com.aeromatch.common.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;

/** Writes the standard {@link ApiError} JSON from servlet filters / security handlers (outside MVC). Messages are generic by design. */
final class SecurityErrorWriter {

    static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    static final String SESSION_EXPIRED = "SESSION_EXPIRED";
    static final String FORBIDDEN = "FORBIDDEN";
    static final String CSRF_INVALID = "CSRF_INVALID";

    private final ObjectMapper objectMapper;

    SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(HttpServletResponse response, int status, String errorCode, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.resetBuffer();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), new ApiError(errorCode, message));
    }
}
