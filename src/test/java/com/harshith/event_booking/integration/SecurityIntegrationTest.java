package com.harshith.event_booking.integration;

import com.harshith.event_booking.support.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityIntegrationTest extends IntegrationTestBase {

    @Autowired
    JwtEncoder appJwtEncoder;

    // ---- public endpoints

    @Test
    void eventBrowsingIsPublic_andSortedByStartTime() throws Exception {
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(8))
                .andExpect(jsonPath("$.content[0].name").value("Jazz in the Park"))
                .andExpect(jsonPath("$.content[1].name").value("Rock Night Live"))
                .andExpect(jsonPath("$.content[2].name").value("Winter Lights Symphony"))
                .andExpect(jsonPath("$.content[7].name").value("Summer Folk Gathering"));
        mockMvc.perform(get("/api/events/{id}", rockNight()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venue").value("City Arena"));
        seatMap(rockNight())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(50));
    }

    @Test
    void missingEvent_is404ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/events/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Event not found"));
    }

    @Test
    void swaggerDocsArePublic_andOfferBearerAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    @Test
    void frontendFilesArePublic_butOnlyForGet() throws Exception {
        // The React build isn't on the test classpath, so these are 404 (not found), never 401.
        for (String path : new String[] {"/", "/index.html", "/assets/index-abc123.js", "/favicon.svg"}) {
            mockMvc.perform(get(path)).andExpect(status().isNotFound());
        }
        // Everything else keeps the old rules.
        mockMvc.perform(post("/")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/some-other-page")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthProbesArePublic_otherActuatorEndpointsAreNot() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        // The full health report (database, Redis details) and other endpoints are not public.
        mockMvc.perform(get("/actuator/health")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    // ---- no token

    @Test
    void protectedEndpointsReturn401WithoutAToken() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":%d,\"seatIds\":[%d]}".formatted(rockNight(), seatId(rockNight(), "A1"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/events")).andExpect(status().isUnauthorized());
    }

    // ---- register and login

    @Test
    void register_thenLogin_thenTheTokenWorksOnApiMe() throws Exception {
        register("alice@example.com", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice@example.com"));

        String body = login("alice@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mockMvc.perform(get("/api/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void duplicateEmail_is409_evenWithDifferentCase() throws Exception {
        register("alice@example.com", PASSWORD).andExpect(status().isCreated());
        register("ALICE@Example.com", "another-password").andExpect(status().isConflict());
    }

    @Test
    void badEmailOrShortPassword_is400_withFieldErrors() throws Exception {
        register("not-an-email", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").value("size must be between 8 and 72"));
    }

    @Test
    void wrongPasswordAndUnknownEmail_give401WithTheSameMessage() throws Exception {
        register("alice@example.com", PASSWORD).andExpect(status().isCreated());
        login("alice@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        login("nobody@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    // ---- forged and expired tokens

    @Test
    void tokenSignedWithADifferentSecret_isRejected() throws Exception {
        var otherKey = new SecretKeySpec("an-attackers-completely-different-secret!".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        String forged = encode(new NimbusJwtEncoder(new ImmutableSecret<>(otherKey)), Instant.now().plusSeconds(3600));
        expectInvalidToken(forged);
    }

    @Test
    void realSignatureWithAnEditedPayload_isRejected() throws Exception {
        String[] parts = tokenFor("alice@example.com").split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String edited = payload.replaceAll("\"uid\":\\d+", "\"uid\":999");
        String forged = parts[0] + "." + base64Url(edited) + "." + parts[2];
        expectInvalidToken(forged);
    }

    @Test
    void unsignedAlgNoneToken_isRejected() throws Exception {
        String[] parts = tokenFor("alice@example.com").split("\\.");
        String forged = base64Url("{\"alg\":\"none\"}") + "." + parts[1] + ".";
        expectInvalidToken(forged);
    }

    @Test
    void expiredTokenWithTheRealSignature_isRejected() throws Exception {
        String expired = encode(appJwtEncoder, Instant.now().minusSeconds(60));
        expectInvalidToken(expired);
    }

    private void expectInvalidToken(String token) throws Exception {
        mockMvc.perform(get("/api/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")));
    }

    private static String encode(JwtEncoder encoder, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("event-booking").subject("alice@example.com").claim("uid", 1L)
                .issuedAt(expiresAt.minusSeconds(3600)).expiresAt(expiresAt)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
