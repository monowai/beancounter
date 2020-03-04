
All of Beancounter Demo is orchestrated with Docker Compose.
```shell script
# Start just KeyCloak and Postgres (prerequisites for auth to work)
docker-compose start postgres keycloak
```

Running the CLI shell

```shell script
# CLI shell
docker-compose -f shell.yml run --rm shell
```
## Keycloak
Beancounter support OAuth 2 JWT. For technical implementation details of how the Auth classes work, please see [jar-auth](../jar-auth/README.md)
 
Keycloak is responsible for authentication, authorization and returning OAuth2 bearer tokens. Configuration of Keycloak is beyond the scope of this document, however this demo project includes an automatically deployed Realm, Client and Scope which is ready to go. 

Keycloak endpoints have to be resolvable from both your local browser _and_ the internal Docker network, as started by `docker-compose`. 
To keep the DNS simple, please add a hosts entry to your `hosts` file (`/etc/hosts on Mac/Linux` or `c:\Windows\System32\Drivers\etc\hosts` on Windows).
```
127.0.0.1	keycloak
```
When you access from your browser on your machine, Keycloak is seen to be running on (localhost or 127.0.0.1) but inside the stack, which uses an internal network and DNS, it is resolved as `http://keycloak`.

You can test the above by logging into the [Keycloak](http://keycloak:9620) admin console using the default user name `admin` and `password` (these are set in `docker-compose.yml`)

### Setting up a user
With the stack running, simply access a secured endpoint and register your account.  
 * Access (http://localhost:4000/login) 
 * Choose "register"
 * Supply all the details

```shell script
$ docker-compose -f shell.yml run --rm shell
bc-shell$ login {registered@user.com}
Password: ********
2020-03-03 06:51:37,289 - Logged in as registered@user.com
bc-shell$ register
bc-shell$ add-portfolio --code "TEST" --name "Test Portfolio" --base-currency USD --currency-code EUR
2020-03-03 06:52:29,694 - Creating portfolio TEST
``` 
 
You can change your password and explore Keycloak user and BC roles via the [KC admin](http://keycloak:9620) interface

### Verify your user
Only the `username` and `password` properties need to be set.
```shell script
curl --location --request POST 'http://keycloak:9620/auth/realms/bc-dev/protocol/openid-connect/token' \
    --header 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'client_id=bc-demo' \
    --data-urlencode 'username=demo@nowhere.com' \
    --data-urlencode 'password=password' \
    --data-urlencode 'grant_type=password'

# You should see a response similar to below.  The access_token can be sent to the API as a Bearer token
{
    "access_token": "eyJhbGciOiJSUzI1NiUS16M1ZScmp4NTV0a1BybW8Wqw...",
    "expires_in": 10800,
    "refresh_expires_in": 86400,
    "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCIgOiAiSldUOi8vae...",
    "token_type": "bearer",
    "not-before-policy": 0,
    "session_state": "59d55507-d5f7-4795-b175-af24da009ff1",
    "scope": "beancounter profile email roles"
}

``` 
   
