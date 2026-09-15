package com.securetransact.ml;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskScoringClient {

    private final MlProperties properties;
    private final RestClient mlRestClient;

    /**
     * Scores a transaction via the ML service. Returns {@link Optional#empty()} when the
     * service is disabled, unreachable, or returns an error — callers must treat ML as a
     * soft signal that never blocks the transaction path.
     */
    public Optional<MlScore> score(MlFeaturesRequest request) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        try {
            MlScoreResponse response = mlRestClient.post()
                    .uri("/score")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MlScoreResponse.class);
            if (response == null) {
                return Optional.empty();
            }
            return Optional.of(new MlScore(response.riskScore(), response.decision(), response.modelVersion()));
        } catch (RestClientResponseException | ResourceAccessException e) {
            log.warn("ML service unavailable ({}); using statistical scoring only", e.getMessage());
            return Optional.empty();
        }
    }
}