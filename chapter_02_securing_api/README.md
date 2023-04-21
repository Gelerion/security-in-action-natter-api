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