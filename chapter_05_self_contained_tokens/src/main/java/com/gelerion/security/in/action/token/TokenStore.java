package com.gelerion.security.in.action.token;

import spark.Request;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public interface TokenStore {

    //Creating a token in the store returns its ID
    String create(Request request, Token token);

    //look up a token by ID
    Optional<Token> read(Request request, String tokenId);

    //logout
    void revoke(Request request, String tokenId);

    class Token {
        public final Instant expiry;
        public final String username;
        //A collection of attributes that you can use to associate information with the token,
        //such as how the user was authenticated or other details that you want to use to make access control decisions
        public final Map<String, String> attributes;

        public Token(Instant expiry, String username) {
            this.expiry = expiry;
            this.username = username;
            this.attributes = new ConcurrentHashMap<>();
        }
    }
}
