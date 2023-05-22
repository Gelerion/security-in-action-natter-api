## Overview
ACLs are simple, but as the number of users and objects that can be accessed through an API grows, the number of ACL 
entries grows along with them. If you have a million users and a million objects, then in the worst case you could 
end up with a billion ACL entries listing the individual permissions of each user for each object. Though that approach 
can work with fewer users, it becomes more of a problem as the user base grows.

### LDAP groups
In many large organizations, including most companies, users are managed centrally in an LDAP (Lightweight Directory 
Access Protocol) directory. LDAP is designed for storing user information and has built-in support for groups. 
You can learn more about LDAP at https://ldap.com/basic-ldap-concepts/. 