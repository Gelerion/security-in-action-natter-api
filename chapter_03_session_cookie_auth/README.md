## Trying it out
The simplest way to get up and running is by opening a terminal in the project folder and using Maven:
```sh
mvn clean compile exec:java -pl chapter_03_session_cookie_auth
```
You should see log output to indicate that Spark has started an embedded Jetty server on port 4567.
You can access the UI from:
```
https://localhost:4567/natter.html
```

### Drawbacks of HTTP authentication
- The user’s password is sent on every API call, increasing the chance of it accidentally being exposed by a bug in one of those operations
- Verifying a password is an expensive operation, and performing this validation on every API call adds a lot of overhead. Modern password-hashing algorithms are designed to take around 100ms for interactive logins, which limits your API to handling 10 operations per CPU core per second
- The dialog box presented by browsers for HTTP Basic authentication is pretty ugly, with not much scope for customization. 
- There is no obvious way for the user to ask the browser to forget the password. Even closing the browser window may not work, and it often requires configuring advanced settings or completely restarting the browser. On a public terminal, this is a serious security problem if the next user can visit pages using your stored password just by clicking the Back button

#### Same-origin policy
By default, browsers allow JavaScript to send HTTP requests only to a server on the same origin that the script was 
loaded from. This is known as the same-origin policy (SOP) and is an important cornerstone of web browser security.

The same-origin policy (SOP) is applied by web browsers to decide whether to allow a page or script loaded from one 
origin to interact with other resources. It applies when other resources are embedded within a page, such as 
by HTML `<img>` or `<script>` tags, and when network requests are made through form submissions or by JavaScript. 
Requests to the same origin are always allowed, but requests to a different origin, known as cross-origin requests, 
are often blocked based on the policy. Many browser APIs available to JavaScript are also restricted by origin, such 
as access to the HTML document itself (via the document object model, or DOM), local data storage, and cookies. 

#### Instructing Spark to serve our HTML and JS files
To instruct Spark to serve your HTML and JavaScript files, you add a `staticFiles` directive to the main method where 
you have configured the API routes.
```
Spark.staticFiles.location("/public");
```
Once you have configured Spark and restarted the API server, you will be able to access the UI 
from `https://localhost:4567/natter.html`
Chrome prompt for `username` and `password` produced automatically when the API asks for HTTP `Basic authentication`.

So, where did this come from? Because your JavaScript client did not supply a username and password on the 
REST API request, the API responded with a standard HTTP 401 Unauthorized status and a WWW-Authenticate header prompting 
for authentication using the Basic scheme. The browser understands the Basic authentication scheme, so it pops up a 
dialog box automatically to prompt the user for a username and password.