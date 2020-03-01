**BeanCounter SSR Viewing App**  
The head of the beast.  Built on RAZZLE this uses SSR techniques. Communicates with services on localhost

Requires KeyCloak + Postrgres to be running. Check out [BC-DEMO](../bc-demo/README.md)  

```
# Start and run against docker-compose orchestrated stack
yarn start:demo
env SVC_POSITION="http://localhost:9600" yarn start
```
