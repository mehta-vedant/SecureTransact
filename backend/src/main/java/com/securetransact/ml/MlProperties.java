package com.securetransact.ml;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ml")
public class MlProperties {

    /** Master switch. When false, no HTTP call is made and scoring is statistical only. */
    private boolean enabled = true;

    /** Base URL of the Flask ML service (e.g. http://localhost:5001). */
    private String baseUrl = "http://localhost:5001";

    /** Per-call connect/read timeout; on timeout/error the engine falls back silently. */
    private int timeoutMs = 500;
}