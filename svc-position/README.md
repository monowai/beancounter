## Compute Positions

Also called Holdings, this service summarises financial attributes across a collection of transactions for an asset
Convert [trade transactions](../svc-google-docs/README.md) into a collection of positions.

```bash
curl -H "Content-Type: application/json" \
        -X POST -d @trades.json http://localhost:9500/ \
        > positions.json    

```

Once you have computed the positions you can request them to be [valued](../svc-md/README.md)

