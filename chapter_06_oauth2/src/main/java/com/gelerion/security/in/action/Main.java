package com.gelerion.security.in.action;

import com.gelerion.security.in.action.controller.AuditController;
import com.gelerion.security.in.action.controller.SpaceController;
import com.gelerion.security.in.action.controller.TokenController;
import com.gelerion.security.in.action.controller.UserController;
import com.gelerion.security.in.action.filter.CorsFilter;
import com.gelerion.security.in.action.token.encrypted.EncryptedJwtTokenStore;
import com.google.common.util.concurrent.RateLimiter;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import org.dalesbred.Database;
import org.dalesbred.result.EmptyResultException;
import org.h2.jdbcx.JdbcConnectionPool;
import org.json.JSONException;
import org.json.JSONObject;
import spark.Request;
import spark.Response;
import spark.Spark;

import javax.crypto.SecretKey;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static spark.Service.SPARK_DEFAULT_PORT;
import static spark.Spark.*;

public class Main {

    @SuppressWarnings("all")
    public static void main(String... args) throws Exception {
        //[web] serve your HTML and JavaScript files
        Spark.staticFiles.location("/public");
        port(args.length > 0 ? Integer.parseInt(args[0]) : SPARK_DEFAULT_PORT);

        //[tls] enable HTTPS support in Spark by adding a call to the secure() static method.
        //The first two arguments to the method give the name of the keystore file containing
        //the server certificate and private key.
        //During an SSL handshake, the server looks up the private key from the keystore, and presents its
        // corresponding public key and certificate to the client
        secure("chapter_02_securing_api/localhost.p12", "changeit", null, null);

        var datasource = JdbcConnectionPool.create("jdbc:h2:mem:natter", "natter", "password");
        var database = Database.forDataSource(datasource);
        createTables(database);

        // using restricted user
        datasource = JdbcConnectionPool.create("jdbc:h2:mem:natter", "natter_api_user", "password");
        database = Database.forDataSource(datasource);

        var spaceController = new SpaceController(database);
        var userController = new UserController(database);
        var auditController = new AuditController(database);

        //[hmac]
        var keyPassword = System.getProperty("keystore.password", "changeit").toCharArray();
        var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new FileInputStream("chapter_05_self_contained_tokens/keystore.p12"), keyPassword);

        var macKey = keyStore.getKey("hmac-key", keyPassword);
        var encKey = keyStore.getKey("aes-key", keyPassword);

        //[jwt] basic
        var algorithm = JWSAlgorithm.HS256;
        var signer = new MACSigner((SecretKey) macKey);
        var verifier = new MACVerifier((SecretKey) macKey);
        //var tokenStore = new SignedJwtTokenStore(signer, verifier, algorithm, "https://localhost:4567");
        var tokenStore = new EncryptedJwtTokenStore((SecretKey) encKey);
        var tokenController = new TokenController(tokenStore);

        //[rate-limiting] allow just 2 API requests per second
        var rateLimiter = RateLimiter.create(2.0d);
        before((request, response) -> {
            if (!rateLimiter.tryAcquire()) {
                halt(429);
            }
        });

        //[cors] CORS preflight requests should be handled before your API requests authentication because credentials
        // are never sent on a preflight request
        before(new CorsFilter(Set.of("https://localhost:9999")));

        //[preventing XSS] validate content-type
        before(((request, response) -> {
            if (request.requestMethod().equals("POST") && !"application/json".equals(request.contentType())) {
                halt(415, new JSONObject().put("error", "Only application/json supported").toString());
            }
        }));

        //[preventing XSS] it is important to set correct type headers on all responses to ensure that data
        // is processed as intended by the client
        afterAfter((request, response) -> {
            response.type("application/json;charset=utf-8");

            //[preventing XSS] add standard security headers
            response.header("X-Content-Type-Options", "nosniff");
            response.header("X-Frame-Options", "DENY");
            response.header("X-XSS-Protection", "0");
            response.header("Cache-Control", "no-store");
            //[xss] https://developer.mozilla.org/en-US/docs/Web/HTTP/CSP
            response.header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; sandbox");
        });

        //If either filter succeeds, then the subject attribute will be populated in the request
        //and subsequent access control checks will pass

        //[authentication]
        // - Check to see if there is an HTTP Basic Authorization header
        // - Decode the credentials using Base64 and UTF-8
        // - If the user exists, then use the Scrypt library to check the password
        before(userController::authenticate);
        //[token-based auth]
        before(tokenController::validateToken);

        //[audit]
        before(auditController::auditRequestStart);
        afterAfter(auditController::auditRequestEnd);

        //[token-based auth] after basic auth and audit log
        // rather than have a /login endpoint, we’ll treat session tokens as a resource and treat logging in
        // as creating a new session resource
        before("/sessions", userController::requireAuthentication);
        //[scoped tokens]
        before("/sessions", tokenController.requireScope("POST", "full_access"));
        post("/sessions", tokenController::login);
        delete("/sessions", tokenController::logout);

        //[access control] enforce authentication -- require that all users are authenticated.
        //This ensures that only genuine users of the API can gain access, while not enforcing any further requirements
        before("/spaces", userController::requireAuthentication);
        post("/spaces", spaceController::createSpace);

        //[acl]
        before("/spaces/*/messages", tokenController.requireScope("POST", "post_message"));
        before("/spaces/:spaceId/messages", userController.requirePermission("POST", "w"));
        post("/spaces/:spaceId/messages", spaceController::postMessage);

        before("/spaces/*/messages/*", tokenController.requireScope("GET", "read_message"));
        before("/spaces/:spaceId/messages/*", userController.requirePermission("GET", "r"));
        get("/spaces/:spaceId/messages/:msgId", spaceController::readMessage);

        before("/spaces/*/messages", tokenController.requireScope("GET", "list_messages"));
        before("/spaces/:spaceId/messages", userController.requirePermission("GET", "r"));
        get("/spaces/:spaceId/messages", spaceController::findMessages);

        //add members
        before("/spaces/*/members", tokenController.requireScope("POST", "add_member"));
        //rwd - only space admin can add other users
        //avoiding privilege escalation attacks - occurs when a user with limited permissions can exploit a bug in the
        // system to grant themselves or somebody else more permissions than they have been granted
        before("/spaces/:spaceId/members", userController.requirePermission("POST", "rwd"));
        post("/spaces/:spaceId/members", spaceController::addMember);

        //[authentication]
        post("/users", userController::registerUser);
        //[audit]
        get("/logs", auditController::readAuditLog);
        before("/expired_tokens", userController::requireAuthentication);
//        delete("/expired_tokens", (request, response) -> {
//            databaseTokenStore.deleteExpiredTokens();
//            return new JSONObject();
//        });

        internalServerError(new JSONObject().put("error", "internal server error").toString());
        notFound(new JSONObject().put("error", "not found").toString());

        exception(IllegalArgumentException.class, Main::badRequest);
        exception(JSONException.class, Main::badRequest);
        exception(EmptyResultException.class, (e, request, response) -> response.status(404));
    }

    private static void badRequest(Exception ex, Request request, Response response) {
        response.status(400);
        response.body(new JSONObject().put("error", ex.getMessage()).toString());
    }

    private static void createTables(Database database) throws Exception {
        var path = Paths.get(requireNonNull(Main.class.getResource("/schema.sql")).toURI());
        database.update(Files.readString(path));
    }
}
