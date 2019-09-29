Micro service to obtain market data from a variety of data providers.
Currently offers support for: 

*   [World Trading Data](https://www.worldtradingdata.com/pricing)    
*   [Alpha Advantage](https://www.alphavantage.co/documentation/)
 
You will need to register for an API key for each provider. See the `application.yml` for how to set the key values.  Data providers can convert market codes as necessary
 
This service returns market data for assets. You can request a price for a single asset or a collection of them  
 
 ```bash 
 curl -X GET http://localhost:9510/AX/AMP
 ```
 
 ```bash
 curl -X POST \
 
   http://localhost:9510/ \
   -H 'Content-Type: application/json' \
   -d '[
   {
     "code": "MSFT",
     "market": {
       "code": "NASDAQ"
     }
   },
   {
     "code": "INTC",
     "market": {
       "code": "NASDAQ"
     }
   },
   {
     "code": "XLV",
     "market": {
       "code": "NASDAQ"
     }
   },
   {
     "code": "AAPL",
     "market": {
       "code": "NASDAQ"
     }
   }
 ]'

```  
 
In the use case of this service you ask svc-position to value the assets, in your position collection, which in turn will obtain the prices from this service - which basically makes the call above
 
 ```bash
 curl -H "Content-Type: application/json" -X POST 
    -d @positions.json http://localhost:9500/value \
    > valuedPositions.json

```
Normally the service will compute the dataproviders date for which to retrieve "current" prices, usually close of business previous day. For testing purposes you can force the date to use by starting the service with the following
```$xslt
# Setting request date for the WTD provider
beancounter.marketdata.provider.WTD.date=yyyy-MM-dd
``` 


ToDo: DB for the prices and history to improve performance