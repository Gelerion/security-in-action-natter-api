# API Security in Action
[API Security in Action](https://learning.oreilly.com/library/view/api-security-in/9781617296024/) teaches you how to create secure APIs for any situation. 
By following this hands-on guide you’ll build a social network API while mastering 
techniques for flexible multi-user security, cloud key management, and lightweight cryptography.
When you’re done, you’ll be able to create APIs that stand up to complex threat models and 
hostile environments.

![Traffic flow](images/traffic_flow.png)

## The Natter API
Natter --the social network for coffee mornings, book groups, and other small gatherings. 
You’ve defined your minimum viable product, somehow received some funding, and now need 
to put together an API and a simple web client. You’ll soon be the new Mark Zuckerberg, 
rich beyond your dreams, and considering a run for president

### Overview of the Natter API
The Natter API is split into two REST endpoints, one for normal users and one for moderators who have special 
privileges to tackle abusive behavior. Interactions between users are built around a concept of social spaces, 
which are invite-only groups. Anyone can sign up and create a social space and then invite their friends to join. 
Any user in the group can post a message to the group, and it can be read by any other member of the group. 
The creator of a space becomes the first moderator of that space.

![Api overview](images/api_overview.png)

* A HTTP POST request to the `/spaces` URI creates a new social space. The user that performs this POST operation 
becomes the owner of the new space. A unique identifier for the space is returned in the response.
* Users can add messages to a social space by sending a POST request to `/spaces/<spaceId>/messages` where `<spaceId>`
is the unique identifier of the space
* The messages in a space can be queried using a GET request to `/spaces/<spaceId>/messages`. 
A `since=<timestamp>` query parameter can be used to limit the messages returned to a recent period.
* Finally, the details of individual messages can be obtained using a GET request to `/spaces/<spaceId>/messages/<messageId>`

## Prerequisites

The following are needed to run the code examples:

- Java 11 or later. See https://adoptopenjdk.net for installers.
- A recent version of [Apache Maven](https://maven.apache.org) - I use 3.6.1.
- For testing, [curl](https://curl.haxx.se). On Mac OS X you should install
  a version of curl linked against OpenSSL rather than Secure Transport, otherwise
  you may need to adjust the examples in the book.
- I highly recommend installing [mkcert](https://github.com/FiloSottile/mkcert)
  for working with SSL certificates from chapter 3 onwards.

The API server for each chapter can be started using the command

    mvn clean compile exec:java

This will start the Spark/Jetty server running on port 4567. See chapter
descriptions for HTTP requests that can be used.

## Chapters
  
- [1 - Secure API development](chapter_01_natter_api)
  - Setting up an example API project
  - Understanding secure development principles
  - Identifying common attacks against APIs
  - Validating input and producing safe output
