package com.harshith.event_booking.security;

import com.harshith.event_booking.model.AppUser;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class TokenService {

    public static final Duration TOKEN_LIFETIME = Duration.ofMinutes(60);
    public static final String UID_CLAIM = "uid";

    private final JwtEncoder jwtEncoder;

    public TokenService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public String createToken(AppUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("event-booking")
                .subject(user.getEmail())
                .claim(UID_CLAIM, user.getId())
                .issuedAt(now)
                .expiresAt(now.plus(TOKEN_LIFETIME))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** The logged-in user's id, read from a token Spring Security has already verified. */
    public static Long userId(Jwt jwt) {
        Number uid = jwt.getClaim(UID_CLAIM);
        return uid.longValue();
    }
}
