## Trying it out
The simplest way to get up and running is by opening a terminal in the project folder and using Maven:
```sh
mvn clean compile exec:java -pl chapter_02_securing_api
```
You should see log output to indicate that Spark has started an embedded Jetty server on port 4567. 
You can then use curl to call your API operation, as in the following example:
```sh
$ curl -i -d '{"name": "test space", "owner": "demo"}' http://localhost:4567/spaces 
```

### Overview
Applying security controls to the Natter API. Encryption prevents information disclosure. 
- Rate-limiting protects availability
- Authentication is used to ensure that users are who they say they are
- Audit logging records who did what, to support accountability
- Access control is then applied to enforce integrity and confidentiality
    
![Security controls](images/scurity_controls.png)

### Rate limiting
Rate-limiting should be the very first security decision made when a request reaches your API. 
Because the goal of rate-limiting is ensuring that your API has enough resources to be able to 
process accepted requests, you need to ensure that requests that exceed your API’s capacities 
are rejected quickly and very early in processing. Other security controls, such as authentication, 
can use significant resources, so rate-limiting must be applied before those processes

You should implement rate-limiting as early as possible, ideally at a load balancer or reverse proxy before requests 
even reach your API servers
  
Often rate-limiting is applied at a reverse proxy, API gateway, or load balancer before the request 
reaches the API, so that it can be applied to all requests arriving at a cluster of servers. By 
handling this at a proxy server, you also avoid excess load being generated on your application servers. 
In this example you’ll apply simple rate-limiting in the API server itself using Google’s Guava library.
Even if you enforce rate-limiting at a proxy server, it is good security practice to also enforce rate 
limits in each server so that if the proxy server misbehaves or is misconfigured, it is still difficult
to bring down the individual servers.

We can then either block and wait until the rate reduces, or you can simply reject the request. 
The standard `HTTP 429 Too Many Requests status` code can be used to indicate that rate-limiting has 
been applied and that the client should try the request again later. You can also 
send a `Retry-After` header to indicate how many seconds the client should wait before trying again.

### Password Authentication
Authentication makes sure that users are who they say they are, preventing spoofing. 
This is essential for accountability, but also a foundation for other security controls.

Apart from rate-limiting (which is applied to all requests regardless of who they come from), authentication is the 
first process we perform. Downstream security controls, such as audit logging and access control, will almost always 
need to know who the user is. It is important to realize that the authentication phase itself shouldn't reject a request 
even if authentication fails. Deciding whether any particular request requires the user to be authenticated is the job 
of access control.
  
#### HTTP Basic authentication
There are many ways of authenticating a user, but one of the most widespread is simple username and 
password authentication. This is a simple standard scheme, specified in RFC [7617](https://tools.ietf.org/html/rfc7617), 
in which the username and password are encoded (using [Base64](https://en.wikipedia.org/wiki/Base64) encoding) and sent 
in a header. 
  
Header example:
```
Authorization: Basic ZGVtbzpjaGFuZ2VpdA==
```
```
jshell> new String(Base64.getDecoder().decode("ZGVtbzpjaGFuZ2VpdA=="), "UTF-8")
=> "demo:changeit"
```

#### Securely store and validate that password
A password hashing algorithm converts each password into a fixed-length random-looking string. When the user tries to
log in, the password they present is hashed using the same algorithm and compared to the hash stored in the database. 
This allows the password to be checked without storing it directly. Modern password hashing algorithms, 
such as `Argon2`, `Scrypt`, `Bcrypt`, or `PBKDF2`, are designed to resist a variety of attacks in case the hashed passwords 
are ever stolen. In particular, they are designed to take a lot of time or memory to process to prevent brute-force 
attacks to recover the passwords.

Before you can authenticate any users, you need some way to register them. For now, you’ll just allow any user to 
register by making a POST request to the /users endpoint, specifying their username and chosen password.
We will store hashed passwords in the `users` table and add an API (`UsersController`) to register users.  
  