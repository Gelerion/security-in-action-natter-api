package com.gelerion.security.in.action.token.encrypted;

import com.gelerion.security.in.action.token.TokenStore;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import spark.Request;

import javax.crypto.SecretKey;
import java.text.ParseException;
import java.util.*;

/**
 * Due to the relative complexity of producing and consuming encrypted JWTs compared to HMAC, you’ll continue using
 * the Nimbus JWT library in this section. Encrypting a JWT with Nimbus requires a few steps
 */
public class EncryptedJwtTokenStore implements TokenStore {
    private final SecretKey encKey;

    public EncryptedJwtTokenStore(SecretKey encKey) {
        this.encKey = encKey;
    }

    @Override
    public String create(Request request, Token token) {
        // First you build a JWT claims set
        var claimsBuilder = new JWTClaimsSet.Builder()
                .subject(token.username)
                .audience("https://localhost:4567")
                .expirationTime(Date.from(token.expiry));

        token.attributes.forEach(claimsBuilder::claim);

        // Then create a JWEHeader object to specify the algorithm and encryption method
        var header = new JWEHeader(JWEAlgorithm.DIR, EncryptionMethod.A128CBC_HS256);
        var jwt = new EncryptedJWT(header, claimsBuilder.build());

        try {
            // Finally, you encrypt the JWT using a DirectEncrypter object initialized with the AES key
            var encrypter = new DirectEncrypter(encKey);
            jwt.encrypt(encrypter);
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }

        return jwt.serialize();
    }

    @Override
    public Optional<Token> read(Request request, String tokenId) {
        try {
            var jwt = EncryptedJWT.parse(tokenId);

            var decryptor = new DirectDecrypter(encKey);
            jwt.decrypt(decryptor);

            var claims = jwt.getJWTClaimsSet();
            if (!claims.getAudience().contains("https://localhost:4567")) {
                return Optional.empty();
            }

            var expiry = claims.getExpirationTime().toInstant();
            var subject = claims.getSubject();
            var token = new Token(expiry, subject);
            var ignore = Set.of("exp", "sub", "aud");
            for (var attr : claims.getClaims().keySet()) {
                if (ignore.contains(attr)) continue;
                token.attributes.put(attr, claims.getStringClaim(attr));
            }

            return Optional.of(token);
        } catch (ParseException | JOSEException e) {
            return Optional.empty();
        }
    }

    @Override
    public void revoke(Request request, String tokenId) {
    }
}
