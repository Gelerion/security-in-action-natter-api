## Overview
In this chapter, you'll integrate an OAuth2 Authorization Server (AS) to allow your users to delegate access to 
third-party clients. By using scoped tokens, users can restrict which parts of the API those clients can access. 
Finally, you’ll see how OAuth provides a standard way to centralize token-based authentication within your organization 
to achieve single sign-on across different APIs and services.
  
The OpenID Connect standard builds on top of OAuth2 to provide a more complete authentication framework when you need 
finer control over how a user is authenticated.

### Scoped tokens
If you wanted to use a third-party app or service to access your email or bank account, you had little choice but to 
give them your username and password and hope they didn't misuse them. Token-based authentication provides a solution 
to this problem by allowing you to generate a long-lived token that you can give to the third-party service instead of 
your password. The service can then use the token to act on your behalf.

Using a token means that you don’t need to give the third-party your password, the tokens you’ve used so far still grant 
full access to APIs as if you were performing actions yourself. The third-party service can use the token to do anything 
that you can do. But you may not trust a third-party to have full access, and only want to grant them partial access.
  
![Github tokens](images/github_tokens.png)

#### Adding scoped tokens to Natter
Define a default set of scopes to grant if the scope parameter is not specified, see TokenController
```
private static final String DEFAULT_SCOPES =
    "create_space post_message read_message list_messages delete_message add_member";
```
  
>**_WARNING_** There is a potential privilege escalation issue to be aware of in this code. A client that is given a 
> scoped token can call this endpoint to exchange it for one with more scopes

```
public JSONObject login(Request request, Response response) {
    var token = new TokenStore.Token(expiry, subject);
    var scope = request.queryParamOrDefault("scope", DEFAULT_SCOPES);   
    token.attributes.put("scope", scope);                              
    var tokenId = tokenStore.create(request, token);
}
```
To enforce the scope restrictions on a token, you can add a new access control filter that ensures that the token used 
to authorize a request to the API has the required scope for the operation being performed.
If there is no scope attribute, then the user directly authenticated the request with Basic authentication. In this case,
you can skip the scope check and let the request proceed. Any client with access to the user’s password would be able 
to issue themselves a token with any scope
  
#### The difference between scopes and permissions
Permissions are typically granted by a central authority that owns the API being accessed. A user does not get to 
choose or change their own permissions. Scopes allow a user to delegate part of their authority to a third-party app, 
restricting how much access they grant using scopes.
![Scopes vs permissions](images/scopes_and_perms.png)