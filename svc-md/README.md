Micro service to obtain market data from a variety of data providers

Currently offers support for: 

 * [World Trading Data](https://www.worldtradingdata.com/pricing)    
 * [Alpha Advantage](https://www.alphavantage.co/documentation/)
 
 You will need to register for an API key for each provider. See the `application.yml` for how to set the key values.
 
 If you've already converted your [Positions](../svc-position/README.md), you can value them via this call
 
 ```bash
 curl -H "Content-Type: application/json" -X POST -d @positions.json http://localhost:9500/value \
    > valuedPositions.json

```

