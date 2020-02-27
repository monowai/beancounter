
All of Beancounter Demo is orchestrated with Docker Compose.
```shell script
# Start just KeyCloak and Postgres (prerequisites for auth to work)
docker-compose start postgres keycloak

```

If you've already built the services, you can create the containers using

```shell script
./build-containers.sh
# Entire stack
docker-compose up -d
# CLI shell
docker-compose -f shell.yml run --rm shell
```
## Keycloak
Beancounter is built to work with OAuth 2 mechanisms. For technical implementation details of how the Auth classes work, please see [jar-auth](../jar-auth/README.md)
 
Keycloak is the mechanism responsible for returning OAuth2 bearer tokens to clients. Configuring Keycloak is beyond the scope of this document and the majority of the setup is in place, so you can explore it in your own time and modify it as necessary. 

Keycloak endpoints need to be resolvable from both your local browser and the internal Docker network started by `docker-compose`. 
When you access from your browser on your machine, keycloks is seen to be running on (localhost or 127.0.0.1) but inside the stack, which uses an internal network and DNS, it is resolved as `http://keycloak`.
To resolve this DNS challenge simply add a hosts entry to your `hosts` file (`/etc/hosts on Mac/Linux` or `c:\Windows\System32\Drivers\etc\hosts` on Windows).
```
127.0.0.1	keycloak
```

You can test the above by logging into the [Keycloak](http://keycloak:9620) admin console using the default user name `admin` and `password` (these are set in `docker-compose.yml`)

BeanCounter keycloak includes an automatically deployed Realm, Client and Scope which is ready to go.  

### Setting up a user
 * From Manage - [add a user](http://keycloak:9620/auth/admin/master/console/#/realms/bc-dev/users).  
    * Toggle _ON_ the Email Verified switch
 * From Credentials - set the password
    * toggling _OFF_ the Temporary switch
 * From Groups - add the user to the `bc-public` group 
 
 That's it. 
 
### Verify your user
Only the `username` and `password` properties need to be set.
```shell script
curl --location --request POST 'http://keycloak:9620/auth/realms/bc-dev/protocol/openid-connect/token' \
    --header 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'client_id=bc-app' \
    --data-urlencode 'username=demo' \
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

## Shell
You can also test your user account from the CLI.
```shell script
docker-compose -f shell.yml run --rm shell
...
bc-shell$ login demo
Password: ********
Logged in as demo
bc-shell$ markets
{
  "data" : [ {
    "code" : "NASDAQ",
    "currency" : {
      "code" : "USD",
      "name" : "Dollar",
....
```


   
