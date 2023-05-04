## Overview
The token database is struggling to cope with this level of traffic. You’ve evaluated different database backends, 
but you’ve heard about stateless tokens that would allow you to get rid of the database entirely. Without a database 
slowing you down, Natter will be able to scale up as the user base continues to grow. You’ll implement self-contained 
tokens securely, and examine some of the security trade-offs compared to database-backed tokens. You’ll also learn 
about the JSON Web Token (JWT) standard that is the most widely used token format today.

### Storing token state on the client
The idea behind stateless tokens is simple. Rather than store the token state in the database, you can instead encode 
that state directly into the token ID and send it to the client. For example, you could serialize the token fields 
into a JSON object, which you then Base64url-encode to create a string that you can use as the token ID. When the token 
is presented back to the API, you then simply decode the token and parse the JSON to recover the attributes 
of the session.
  
see [JsonTokenStore](src/main/java/com/gelerion/security/in/action/token/JsonTokenStore.java)

##### Protecting JSON tokens with HMAC
Anybody can log in to the API and then edit the encoded token in their browser to change their username 
or other security attributes! In fact, they can just create a brand-new token themselves without ever 
logging in. You can fix that by reusing the `HmacTokenStore`
  
```
TokenStore tokenStore = new JsonTokenStore();
tokenStore = new HmacTokenStore(tokenStore, macKey);
var tokenController = new TokenController(tokenStore);
```