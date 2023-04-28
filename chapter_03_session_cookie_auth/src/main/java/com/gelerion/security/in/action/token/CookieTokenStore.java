package com.gelerion.security.in.action.token;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

import spark.Request;
import java.util.Optional;

/**
 * Cookie-based sessions are so widespread that almost every web framework for any language has built-in support
 * for creating such session cookies, and Spark is no exception. In this section you’ll build a TokenStore
 * implementation based on Spark’s session cookie support. To access the session associated with a request,
 * you can use the request.session() method
 */
public class CookieTokenStore implements TokenStore {

    @Override
    public String create(Request request, Token token) {
        //Suffers from a vulnerability known as session fixation!
        //You can prevent session fixation attacks by ensuring that any existing session is invalidated after a user authenticates
        var session = request.session(true);

        session.attribute("username", token.username);
        session.attribute("expiry", token.expiry);
        session.attribute("attrs", token.attributes);

        return session.id();
    }

    @Override
    public Optional<Token> read(Request request, String tokenId) {
        var session = request.session(false);
        if (session == null) {
            return Optional.empty();
        }

        var token = new Token(session.attribute("expiry"), session.attribute("username"));
        token.attributes.putAll(session.attribute("attrs"));

        return Optional.of(token);
    }
}
