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
