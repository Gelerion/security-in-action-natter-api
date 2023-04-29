package com.gelerion.security.in.action.token;

import spark.Request;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

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
        //var session = request.session(true);

        //check if the client has an existing session
        var session = request.session(false);
        if (session != null) {
            //Invalidate any existing session to ensure that the next call to request.session(true) will create a new one
            session.invalidate();
        }
        session = request.session(true);

        session.attribute("username", token.username);
        session.attribute("expiry", token.expiry);
        session.attribute("attrs", token.attributes);

        //[csrf attacks] return the SHA-256 hash of the session cookie, Base64url-encoded
        return Base64url.encode(sha256(session.id()));
    }

    @Override
    public Optional<Token> read(Request request, String tokenId) {
        var session = request.session(false);
        if (session == null) {
            return Optional.empty();
        }

        var provided = Base64url.decode(tokenId);
        var computed = sha256(session.id());

        //constant time equals
        if (!MessageDigest.isEqual(computed, provided)) {
            return Optional.empty();
        }

        var token = new Token(session.attribute("expiry"), session.attribute("username"));
        token.attributes.putAll(session.attribute("attrs"));

        return Optional.of(token);
    }

    @Override
    public void revoke(Request request, String tokenId) {
        var session = request.session(false);
        if (session == null) return;

        var provided = Base64url.decode(tokenId);
        var computed = sha256(session.id());

        if (!MessageDigest.isEqual(computed, provided)) {
            return;
        }

        session.invalidate();
    }

    static byte[] sha256(String tokenId) {
        try {
            var sha256 = MessageDigest.getInstance("SHA-256");
            return sha256.digest(tokenId.getBytes(UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
