
Micro service to convert [trade transactions](../svc-google-docs/README.md) into a collection of positions.

```bash
curl -H "Content-Type: application/json" \
        -X POST -d @trades.json http://localhost:9500/ \
        > positions.json    

```

Once you have computed the positions you can request them to be [valued](../svc-md/README.md)

