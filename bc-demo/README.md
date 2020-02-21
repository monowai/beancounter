
Start the stack orchestrated by compose
If you've already built all the services, you can create the containers using

```
./build-containers.sh
# Wait for them to complete
docker-compose up -d
# you can run the CLI shell with
docker-compose run --rm shell
```

http://localhost:4000 to access the webview

Shell automatically exits if you start the stack with `-d`. You can get to the shell with the following
```
docker-compose run --rm shell
```

## Keycloak
To make Keycloak work, you need to add the following line to your hosts file (``/etc/hosts on Mac/Linux`, `c:\Windows\System32\Drivers\etc\hosts` on Windows).

```
127.0.0.1	keycloak
```

This is because you will access your application with a browser on your machine (which name is localhost, or 127.0.0.1), but inside Docker it will run in its own container, which name is keycloak.

You can test the above by logging into the Keycloak admin console
```
http://keycloak:9620
```
# Create a realm

