package com.gelerion.security.in.action.token;

import java.security.SecureRandom;
import java.sql.*;
import java.util.Optional;

import org.dalesbred.Database;
import org.json.JSONObject;
import org.slf4j.*;
import spark.Request;

import static com.gelerion.security.in.action.token.CookieTokenStore.sha256;

public class DatabaseTokenStore implements TokenStore {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseTokenStore.class);

    private final Database database;
    private final SecureRandom secureRandom;

    public DatabaseTokenStore(Database database) {
        this.database = database;
        //To ensure that Java uses the non-blocking /dev/urandom device for seeding the SecureRandom class,
        // pass the option -Djava.security.egd=file: /dev/urandom to the JVM
        this.secureRandom = new SecureRandom();
    }

    private String randomId() {
        //[tokens] we'ill use 160-bit token IDs
        var bytes = new byte[20];
        secureRandom.nextBytes(bytes);
        return Base64url.encode(bytes);
    }

    @Override
    public String create(Request request, Token token) {
        var tokenId = randomId();
        var attrs = new JSONObject(token.attributes).toString();

        database.updateUnique("INSERT INTO tokens(token_id, user_id, expiry, attributes) " +
                        "VALUES(?, ?, ?, ?)", hash(tokenId), token.username, token.expiry, attrs);

        return tokenId;
    }

    @Override
    public Optional<Token> read(Request request, String tokenId) {
        return database.findOptional(this::readToken,
                "SELECT user_id, expiry, attributes FROM tokens WHERE token_id = ?", hash(tokenId));
    }

    @Override
    public void revoke(Request request, String tokenId) {
        database.update("DELETE FROM tokens WHERE token_id = ?",
                hash(tokenId));
    }

    private String hash(String tokenId) {
        var hash = sha256(tokenId);
        return Base64url.encode(hash);
    }

    private Token readToken(ResultSet resultSet)
            throws SQLException {
        var username = resultSet.getString(1);
        var expiry = resultSet.getTimestamp(2).toInstant();
        var json = new JSONObject(resultSet.getString(3));

        var token = new Token(expiry, username);
        for (var key : json.keySet()) {
            token.attributes.put(key, json.getString(key));
        }
        return token;
    }
}
