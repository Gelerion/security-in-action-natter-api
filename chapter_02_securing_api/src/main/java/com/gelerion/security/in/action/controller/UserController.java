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
}
