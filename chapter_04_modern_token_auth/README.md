## Overview
You’ll learn about alternatives to cookies using HTML 5 Web Storage and the standard Bearer authentication 
scheme for token-based authentication. You’ll enable cross-origin resource sharing (CORS) to allow 
cross-domain requests from the new site.
  
### Allowing cross-domain requests with CORS
If you need to communticate with existing API from different domain, the same-origin policy (SOP) throws up several 
problems for cookie-based authentication: 
- Attempting to send a login request from the new site is blocked because the JSON Content-Type header is disallowed by the SOP
- Even if you could send the request, the browser will ignore any Set-Cookie headers on a cross-origin response, so the session cookie will be discarded
- You also cannot read the anti-CSRF token, so cannot make requests from the new site even if the user is already logged in

Moving to an alternative token storage mechanism solves only the second issue, but if you want to allow 
cross-origin requests to your API from browser clients, you’ll need to solve the others. The solution is 
the CORS standard, introduced in 2013 to allow the SOP to be relaxed for some cross-origin requests
  
### CORS headers
You can learn more about CORS headers from Mozilla’s excellent article 
at https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS. The `Access-Control-Allow-Origin` and 
`Access-Control-Allow-Credentials` headers can be sent in the response to the preflight request and 
in the response to the actual request, whereas the other headers are sent only in response to 
the preflight request

> **_NOTE_** Because cookies are considered a credential by CORS, you need to return an 
> Access-Control-Allow-Credentials: true header from preflight requests; otherwise, the browser will not 
> send the session cookie. 
  
### Try it out
Start a server
```sh
mvn clean compile exec:java
```
Now start a second copy of the Natter UI by running the following command:
```sh
mvn clean compile exec:java -Dexec.args=9999
```