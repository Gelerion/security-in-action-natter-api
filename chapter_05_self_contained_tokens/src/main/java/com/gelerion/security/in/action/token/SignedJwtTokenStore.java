package com.gelerion.security.in.action.token;

import javax.crypto.SecretKey;
import java.text.ParseException;
import java.util.*;

import com.nimbusds.jose.*;
import com.nimbusds.jwt.*;
import spark.Request;

/**
 * The Nimbus library requires a <b>JWSSigner</b> object for generating signatures, and a <b>JWSVerifier</b> for verifying them.
 * </n>
 * These objects can often be used with several algorithms, so you should also pass in the specific algorithm to use as
 * a separate <b>JWSAlgorithm</b> object.
 * </n>
 * Finally, you should also pass in a value to use as the audience for the generated JWTs. This should usually be the
 * base URI of the API server, such as https:/ /localhost:4567. By setting and verifying the audience claim, you ensure
 * that a JWT can’t be used to access a different API, even if they happen to use the same cryptographic key.
 */
public class SignedJwtTokenStore implements TokenStore {
    private final JWSSigner signer;
    private final JWSVerifier verifier;
    private final JWSAlgorithm algorithm;
    private final String audience;

    public SignedJwtTokenStore(JWSSigner signer,
                               JWSVerifier verifier,
                               JWSAlgorithm algorithm,
                               String audience) {
        this.signer = signer;
        this.verifier = verifier;
        this.algorithm = algorithm;
        this.audience = audience;
    }

    @Override
    public String create(Request request, Token token) {
        /*
        To produce the JWT you first build the claims set, set the sub claim to the username, the exp claim to the
        token expiry time, and the aud claim to the audience value you got from the constructor.
         */
        var claimsSet = new JWTClaimsSet.Builder()
                .subject(token.username)
                .audience(audience)
                .expirationTime(Date.from(token.expiry))
                .claim("attrs", token.attributes)
                .build();

        var header = new JWSHeader(algorithm);
        var jwt = new SignedJWT(header, claimsSet);

        /*
        To sign the JWT, you then set the correct algorithm in the header and use the JWSSigner object to calculate
        the signature. The serialize() method will then produce the JWS Compact Serialization of the JWT to return
        as the token identifier
         */
        try {
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Token> read(Request request, String tokenId) {
        try {
            var jwt = SignedJWT.parse(tokenId);
            if (!jwt.verify(verifier)) {
                throw new JOSEException("Invalid signature");
            }

            var claims = jwt.getJWTClaimsSet();
            if (!claims.getAudience().contains(audience)) {
                throw new JOSEException("Incorrect audience");
            }

            var expiry = claims.getExpirationTime().toInstant();
            var subject = claims.getSubject();
            var token = new Token(expiry, subject);

            var attrs = claims.getJSONObjectClaim("attrs");
            attrs.forEach((key, value) ->
                    token.attributes.put(key, (String) value));

            return Optional.of(token);
        } catch (ParseException | JOSEException e) {
            return Optional.empty();
        }
    }

    @Override
    public void revoke(Request request, String tokenId) {
        // TODO
    }
}
