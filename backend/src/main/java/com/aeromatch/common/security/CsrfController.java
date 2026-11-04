package com.aeromatch.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets the SPA (including SSR) obtain the CSRF header name and the current token. Public by design (GET only). */
@RestController
@RequestMapping("/api/v1/auth")
class CsrfController {

    /** Response contract (fixed; the frontend codes against it): {"headerName":"X-XSRF-TOKEN","token":"..."}. */
    record CsrfTokenResponse(String headerName, String token) {}

    @GetMapping("/csrf")
    ResponseEntity<CsrfTokenResponse> csrf(HttpServletRequest request) {
        DeferredCsrfToken deferred = (DeferredCsrfToken) request.getAttribute(DeferredCsrfToken.class.getName());
        CsrfToken token = deferred.get(); // raw token (same value as the XSRF-TOKEN cookie)
        return ResponseEntity.ok(new CsrfTokenResponse(token.getHeaderName(), token.getToken()));
    }
}
