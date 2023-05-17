package com.gelerion.security.in.action.filter;

import spark.*;

import java.util.Set;

import static spark.Spark.halt;

public class CorsFilter implements Filter {
    private final Set<String> allowedOrigins;

    public CorsFilter(Set<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void handle(Request request, Response response) {
        //[cors] If the origin is allowed, then add the basic CORS headers to the response
        var origin = request.headers("Origin");
        if (origin != null && allowedOrigins.contains(origin)) {
            response.header("Access-Control-Allow-Origin", origin);
            //[cors] include a Vary: Origin header to ensure the browser and any network proxies only cache
            // the response for this specific requesting origin
            response.header("Vary", "Origin");
        }

        if (isPreflightRequest(request)) {
            if (origin == null || !allowedOrigins.contains(origin)) {
                //[cors] it is recommended to return a 403 Forbidden error for preflight requests from unauthorized origin
                halt(403);
            }

            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
            response.header("Access-Control-Allow-Methods", "GET, POST, DELETE");
            //[cors] 204 No Content response for successful preflight requests
            halt(204);
        }
    }

    //[cors] Preflight requests use the HTTP OPTIONS method and include the CORS request method header
    private boolean isPreflightRequest(Request request) {
        return "OPTIONS".equals(request.requestMethod()) &&
                request.headers().contains("Access-Control-Request-Method");
    }
}
