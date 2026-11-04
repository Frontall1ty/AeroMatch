package com.aeromatch.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Session cookie: HttpOnly, SameSite=Lax, Path=/, no Max-Age (browser-session cookie), no Domain.
 * Name and Secure are profile properties (prod may use the __Host- prefix; local runs on plain http).
 */
@Configuration
class SessionConfig {

    @Bean
    CookieSerializer cookieSerializer(AeroMatchSecurityProperties props) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(props.session().cookieName());
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(props.cookieSecure());
        serializer.setSameSite("Lax");
        serializer.setCookiePath("/");
        // cookieMaxAge deliberately left at its default (-1): no Max-Age/Expires.
        return serializer;
    }
}
