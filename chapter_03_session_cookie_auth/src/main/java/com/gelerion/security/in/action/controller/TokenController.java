package com.gelerion.security.in.action.controller;

import java.time.temporal.ChronoUnit;

import com.gelerion.security.in.action.token.TokenStore;
import org.json.JSONObject;
import spark.*;

import static java.time.Instant.now;

public class TokenController {
    private final TokenStore tokenStore;

    public TokenController(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    public JSONObject login(Request request, Response response) {
        String subject = request.attribute("subject");
        var expiry = now().plus(10, ChronoUnit.MINUTES);

        var token = new TokenStore.Token(expiry, subject);
        var tokenId = tokenStore.create(request, token);

        response.status(201);
        //[csrf] this will now return the SHA-256 hashed version, because that is what the CookieTokenStore returns
        //this has an added security benefit that the real session ID is now never exposed to JavaScript, even in that response
        return new JSONObject()
                .put("token", tokenId);
    }

    /*
    The existing HTTP Basic authentication filter populates the subject attribute on the request if valid
    credentials are found, and later access control filters check for the presence of this subject attribute.
    You can allow requests with a session cookie to proceed by implementing the same contract: if a valid session
    cookie is present, then extract the username from the session and set it as the subject attribute in the request
     */
    public void validateToken(Request request, Response response) {
        //read the CSRF token from the X-CSRF-Token header.
        var tokenId = request.headers("X-CSRF-Token");
        if (tokenId == null) return;

        tokenStore.read(request, tokenId).ifPresent(token -> {
            if (now().isBefore(token.expiry)) {
                request.attribute("subject", token.username);
                token.attributes.forEach(request::attribute);
            }
        });
    }

    public JSONObject logout(Request request, Response response) {
        var tokenId = request.headers("X-CSRF-Token");
        if (tokenId == null)
            throw new IllegalArgumentException("missing token header");

        tokenStore.revoke(request, tokenId);

        response.status(200);
        return new JSONObject();
    }
}
