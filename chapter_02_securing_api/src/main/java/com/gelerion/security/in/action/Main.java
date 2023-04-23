package com.gelerion.security.in.action;

import com.gelerion.security.in.action.controller.AuditController;
import com.gelerion.security.in.action.controller.SpaceController;
import com.gelerion.security.in.action.controller.UserController;
import org.dalesbred.Database;
import org.dalesbred.result.EmptyResultException;
import org.h2.jdbcx.JdbcConnectionPool;
import org.json.JSONException;
import org.json.JSONObject;
import spark.Request;
import spark.Response;

import java.nio.file.Files;
import java.nio.file.Paths;

import com.google.common.util.concurrent.RateLimiter;
import static java.util.Objects.requireNonNull;
import static spark.Spark.*;

public class Main {

    public static void main(String... args) throws Exception {
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

        //[rate-limiting] allow just 2 API requests per second
        var rateLimiter = RateLimiter.create(2.0d);
        before((request, response) -> {
            if (!rateLimiter.tryAcquire()) {
                halt(429);
            }
        });

        //[preventing XSS] validate content-type
        before(((request, response) -> {
            if (request.requestMethod().equals("POST") && !"application/json".equals(request.contentType())) {
                halt(415, new JSONObject().put("error", "Only application/json supported").toString());
            }
        }));

        // it is important to set correct type headers on all responses to ensure that data
        // is processed as intended by the client
        afterAfter((request, response) -> {
            response.type("application/json;charset=utf-8");

            //[preventing XSS] add standard security headers
            response.header("X-Content-Type-Options", "nosniff");
            response.header("X-Frame-Options", "DENY");
            response.header("X-XSS-Protection", "0");
            response.header("Cache-Control", "no-store");
            response.header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; sandbox");
        });

        //[authentication]
        // - Check to see if there is an HTTP Basic Authorization header
        // - Decode the credentials using Base64 and UTF-8
        // - If the user exists, then use the Scrypt library to check the password
        before(userController::authenticate);

        //[audit]
        before(auditController::auditRequestStart);
        afterAfter(auditController::auditRequestEnd);

        post("/spaces", spaceController::createSpace);

        post("/spaces/:spaceId/messages", spaceController::postMessage);
        get("/spaces/:spaceId/messages/:msgId", spaceController::readMessage);
        get("/spaces/:spaceId/messages", spaceController::findMessages);

        //[authentication]
        post("/users", userController::registerUser);
        //[audit]
        get("/logs", auditController::readAuditLog);

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
