package com.gelerion.security.in.action.token;

import javax.crypto.Mac;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Optional;

import spark.Request;

public class HmacTokenStore implements TokenStore {
    private final TokenStore delegate;
    private final Key macKey;

    public HmacTokenStore(TokenStore delegate, Key macKey) {
        this.delegate = delegate;
        this.macKey = macKey;
    }

    /*
    Rather than storing the authentication tag in the database alongside the token ID, you’ll instead leave that as-is.
    Before you return the token ID to the client, you’ll compute the HMAC tag and append it to the encoded token
     */
    @Override
    public String create(Request request, Token token) {
        var tokenId = delegate.create(request, token);
        var tag = hmac(tokenId);

        return tokenId + '.' + Base64url.encode(tag);
    }

    private byte[] hmac(String tokenId) {
        try {
            var mac = Mac.getInstance(macKey.getAlgorithm());
            mac.init(macKey);
            return mac.doFinal(tokenId.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    /*
    When the client sends a request back to the API including the token, you can validate the authentication tag.
    If it is valid, then the tag is stripped off and the original token ID passed to the database token store.
    If the tag is invalid or missing, then the request can be immediately rejected without any database lookups,
    preventing any timing attacks. Because an attacker with access to the database cannot create a valid authentication
    tag, they can’t use any stolen tokens to access the API and they can’t create their own tokens by inserting
    records into the database
     */
    @Override
    public Optional<Token> read(Request request, String tokenId) {
        var index = tokenId.lastIndexOf('.');
        if (index == -1) {
            return Optional.empty();
        }
        var realTokenId = tokenId.substring(0, index);

        var provided = Base64url.decode(tokenId.substring(index + 1));
        var computed = hmac(realTokenId);

        if (!MessageDigest.isEqual(provided, computed)) {
            return Optional.empty();
        }

        return delegate.read(request, realTokenId);
    }

    @Override
    public void revoke(Request request, String tokenId) {
        var index = tokenId.lastIndexOf('.');
        if (index == -1) return;
        var realTokenId = tokenId.substring(0, index);

        var provided = Base64url.decode(tokenId.substring(index + 1));
        var computed = hmac(realTokenId);

        if (!MessageDigest.isEqual(provided, computed)) {
            return;
        }

        delegate.revoke(request, realTokenId);
    }
}
