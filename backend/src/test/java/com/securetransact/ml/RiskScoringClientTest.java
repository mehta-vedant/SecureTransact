package com.securetransact.ml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

class RiskScoringClientTest {

    private MockRestServiceServer mockServer;
    private RiskScoringClient client;
    private MlProperties properties;
    private MlFeaturesRequest request;

    @BeforeEach
    void setUp() {
        properties = new MlProperties();
        properties.setBaseUrl("http://localhost:5001");
        properties.setTimeoutMs(1000);

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new RiskScoringClient(properties, builder.build());

        request = new MlFeaturesRequest(
                new BigDecimal("500"),
                "2026-09-15T10:00:00",
                new BigDecimal("300"),
                new BigDecimal("100"),
                "2026-03-01T00:00:00",
                2, 5, new BigDecimal("280"), 2, false, false);
    }

    @Test
    void shouldReturnScoreOnSuccess() {
        mockServer.expect(requestTo("/score"))
                .andRespond(withSuccess(
                        "{\"riskScore\": 82, \"decision\": \"BLOCK\", \"modelVersion\": \"isolation-forest-v1\"}",
                        MediaType.APPLICATION_JSON));

        Optional<MlScore> result = client.score(request);

        assertTrue(result.isPresent());
        assertEquals(82, result.get().riskScore());
        assertEquals("BLOCK", result.get().decision());
        mockServer.verify();
    }

    @Test
    void shouldReturnEmptyOnServiceError() {
        mockServer.expect(requestTo("/score"))
                .andRespond(withStatus(SERVICE_UNAVAILABLE).body("{\"error\": \"no model\"}"));

        Optional<MlScore> result = client.score(request);

        assertTrue(result.isEmpty());
        mockServer.verify();
    }

    @Test
    void shouldReturnEmptyWhenDisabled() {
        properties.setEnabled(false);

        Optional<MlScore> result = client.score(request);

        assertTrue(result.isEmpty());
    }
}
