package com.gelerion.security.in.action.controller;

import com.lambdaworks.crypto.*;
import org.dalesbred.*;
import org.json.*;
import spark.*;

import java.nio.charset.*;
import java.util.*;

import static spark.Spark.*;

public class UserController {
    private static final String USERNAME_PATTERN = "[a-zA-Z][a-zA-Z0-9]{1,29}";

    private final Database database;

    public UserController(Database database) {
        this.database = database;
    }

    public JSONObject registerUser(Request request, Response response) {
        var json = new JSONObject(request.body());
        var username = json.getString("username");
        var password = json.getString("password");

        if (!username.matches(USERNAME_PATTERN)) {
            throw new IllegalArgumentException("invalid username");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("password must be at least 8 characters");
        }

        /*
        We use Scrypt library to hash the password.

        Scrypt takes several parameters to tune the amount of time and memory that it will use. You do not need
        to understand these numbers, just know that larger numbers will use more CPU time and memory. You can use
        the recommended parameters as of 2019 (see https://blog.filippo.io/the-scrypt-parameters/ for a discussion
        of Scrypt parameters), which should take around 100ms on a single CPU and 32MiB of memory.

        This may seem an excessive amount of time and memory, but these parameters have been carefully chosen based
        on the speed at which attackers can guess passwords.
         */
        var hash = SCryptUtil.scrypt(password, 32768, 8, 1);

        /*
        The Scrypt library generates a unique random salt value for each password hash. The hash string that gets
        stored in the database includes the parameters that were used when the hash was generated, as well as this
        random salt value. This ensures that you can always recreate the same hash in the future, even if you change
        the parameters. The Scrypt library will be able to read this value and decode the parameters when it verifies
        the hash.
         */
        database.updateUnique("INSERT INTO users(user_id, pw_hash) VALUES(?, ?)", username, hash);

        response.status(201);
        response.header("Location", "/users/" + username);
        return new JSONObject().put("username", username);
    }

    /**
     * To authenticate a user, you’ll extract the username and password from the HTTP Basic authentication header,
     * look up the corresponding user in the database, and finally verify the password matches the hash stored for
     * that user. Behind the scenes, the Scrypt library will extract the salt from the stored password hash, then hash
     * the supplied password with the same salt and parameters, and then finally compare the hashed password with the
     * stored hash. If they match, then the user must have presented the same password and so authentication succeeds,
     * otherwise it fails.
     */
    public void authenticate(Request request, Response response) {
        var authHeader = request.headers("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return;
        }

        var offset = "Basic ".length();
        var credentials = new String(Base64.getDecoder()
                .decode(authHeader.substring(offset)), StandardCharsets.UTF_8);

        var components = credentials.split(":", 2);
        if (components.length != 2) {
            throw new IllegalArgumentException("invalid auth header");
        }

        var username = components[0];
        var password = components[1];

        if (!username.matches(USERNAME_PATTERN)) {
            throw new IllegalArgumentException("invalid username");
        }

        var hash = database.findOptional(String.class, "SELECT pw_hash FROM users WHERE user_id = ?", username);

        /*
         Behind the scenes, the Scrypt library will extract the salt from the stored password hash, then hash the
         supplied password with the same salt and parameters, and then finally compare the hashed password with
         the stored hash
         */
        if (hash.isPresent() && SCryptUtil.check(password, hash.get())) {
            request.attribute("subject", username);
        }
    }

    /*
     If no subject attribute is found, then it rejects the request with a 401 status code and adds a standard
     WWW-Authenticate header to inform the client that the user should authenticate with Basic authentication.
     */
    public void requireAuthentication(Request request, Response response) {
        if (request.attribute("subject") == null) {
            //for custom login form -- stop the browser popping up the ugly default login box
            //response.header("WWW-Authenticate", "Basic realm=\"/\", charset=\"UTF-8\"");
            response.header("WWW-Authenticate", "Bearer");
            halt(401);
        }
    }

    public Filter requirePermission(String method, String permission) {
        return (request, response) -> {
            // ignore requests that don’t match the request method
            if (!method.equalsIgnoreCase(request.requestMethod())) {
                return;
            }

            // check if the user is authenticated
            requireAuthentication(request, response);

            var spaceId = Long.parseLong(request.params(":spaceId"));
            var username = (String) request.attribute("subject");

            var perms = database.findOptional(String.class,
                    "SELECT perms FROM permissions WHERE space_id = ? AND user_id = ?",
                    spaceId, username).orElse("");

            if (!perms.contains(permission)) {
                halt(403);
            }
        };
    }

}
