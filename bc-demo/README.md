
Start the stack orchestrated by compose
If you've already built all the services, you can create the containers using

```
./build-containers.sh
# Wait for them to complete
docker-compose up -d
# or
docker-compose up --rm shell
```


http://localhost:4000 to access the webview

Shell automatically exits if you start the stack with `-d`. You can get to the shell with the following
```
docker-compose run --rm shell
```

