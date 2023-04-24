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
  
- We store hashed passwords in the `users` table and add an API (`UsersController`) to register users and authenticate users.  
- We added an authentication filter that is called before every API call.
```
 before(userController::authenticate);
```
- We updated the Create Space operation to check that the owner field matches the currently authenticated user. 
This also allows you to skip validating the username, because you can rely on the authentication service to have done that already.


We’ve now enabled authentication for your API--every time a user makes a claim about their identity, they are required 
to authenticate to provide proof of that claim. You’re not yet enforcing authentication on all API calls, so you can 
still read messages without being authenticated.
```
$ curl -d '{"name":"test space","owner":"demo"}' -H 'Content-Type: application/json' http://localhost:4567/spaces

{"error":"owner must match authenticated user"}
```
Register a user:
```sh
$ curl -d '{"username":"demo","password":"password"}' -H 'Content-Type: application/json' http://localhost:4567/users
```
Create Space request with correct authentication credentials
```sh
curl -u demo:password -d '{"name":"test space","owner":"demo"}' -H 'Content-Type: application/json' http://localhost:4567/spaces
```
  
### Encryption to keep data private (TLS)
In this case, sending passwords in clear text is a pretty big vulnerability, so let’s fix that by enabling HTTPS. 
HTTPS is normal HTTP, but the connection occurs over Transport Layer Security (TLS), which provides encryption and 
integrity protection. Once correctly configured, TLS is largely transparent to the API because it occurs at a lower 
level in the protocol stack and the API still sees normal requests and responses.

HTTPS is used to encrypt and protect data being transmitted (in transit) to and from you API

> **_NOTE:_**  Transport Layer Security (TLS) is a protocol that sits on top of TCP/IP and provides several basic 
> security functions to allow secure communication between a client and a server. Early versions of TLS were known 
> as the Secure Socket Layer, or SSL, and you’ll often still hear TLS referred to as SSL. Application protocols 
> that use TLS often have an S appended to their name, for example HTTPS or LDAPS, to stand for “secure.”

TLS ensures confidentiality and integrity of data transmitted between the client and server. It does this by encrypting 
and authenticating all data flowing between the two parties. The first time a client connects to a server, 
a TLS handshake is performed in which the server authenticates to the client, to guarantee that the client 
connected to the server it wanted to connect to (and not to a server under an attacker’s control). 
Then fresh cryptographic keys are negotiated for this session and used to encrypt and authenticate 
every request and response from then on.

#### Enabling HTTPS
Enabling HTTPS support in Spark is straightforward. First, you need to generate a certificate that the API will use to 
authenticate itself to its clients. When a client connects to your API it will use a URI that includes the hostname 
of the server the API is running on, for example `api.example.com`. The server must present a certificate, signed 
by a trusted certificate authority (CA), that says that it really is the server for `api.example.com`. 
If an invalid certificate is presented, or it doesn't match the host that the client wanted to connect to, then the 
client will abort the connection. Without this step, the client might be tricked into connecting to the wrong server 
and then send its password or other confidential data to the imposter.

Because you’re enabling HTTPS for development purposes only, you could use a self-signed certificate.
A tool called [mkcert](https://mkcert.dev) simplifies the process considerably. Follow the instructions on the `mkcert`
homepage to install it, and then run.
```sh
mkcert -install
```
You can now generate a certificate for your Spark server running on localhost. By default, `mkcert` generates 
certificates in Privacy Enhanced Mail (`PEM`) format. For Java, you need the certificate in `PKCS#12` format, 
so run the following command in the root folder of the Natter project to generate a certificate for localhost:
```sh
mkcert -pkcs12 localhost
```
The certificate and private key will be generated in a file called `localhost.p12`.
By default, the password for this file is `changeit`
  
You can now enable HTTPS support in Spark by adding a call to the `secure()` static method
```
secure("localhost.p12", "changeit", null, null);
```

#### Testing the API
If curl refuses to connect, you can use the `--cacert` option to curl to tell it to trust the mkcert certificate:
```sh
$ curl --cacert "$(mkcert -CAROOT)/rootCA.pem" -d '{"username":"demo","password":"password"}' \
-H 'Content-Type: application/json' https://localhost:4567/users
```
  
### Audit logging
Audit logging should occur both before a request is processed and after it completes. When implemented 
as a filter, it should be placed after authentication, so that you know who is performing each action, 
but before access control checks so that you record operations that were attempted but denied.
  
> In a production environment you typically will want to send audit logs to a centralized log collection and analysis tool

We split the logging into two filters, one that occurs before the request is processed 
(after authentication), and one that occurs after the response has been produced. We’ll also allow 
access to the logs to anyone for illustration purposes. You should normally lock down audit logs to 
only a small number of trusted users, as they are often sensitive in themselves.
  
Reading the log:
```sh
$ curl pem https://localhost:4567/logs | jq
```
  
### Access control
Access control should happen after authentication, so that you know who is trying to perform the action.
If the request is granted, then it can proceed through to the application logic. However, if it is 
denied by the access control rules, then it should be failed immediately, and an error response 
returned to the user. The two main HTTP status codes for indicating that access has been denied
are `401 Unauthorized` and `403 Forbidden`
  
Usually access control consist of:
- Enforcing authentication
- Access control lists