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
        //Because the CookieTokenStore can determine the token associated with a request by looking at the cookies,
        //you can leave the tokenId argument null for now when looking up the token in the tokenStore
        // WARNING: CSRF attack possible
        tokenStore.read(request, null).ifPresent(token -> {
            if (now().isBefore(token.expiry)) {
                request.attribute("subject", token.username);
                token.attributes.forEach(request::attribute);
            }
        });
    }
}
