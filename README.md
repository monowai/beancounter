[![CircleCI](https://circleci.com/gh/monowai/beancounter.svg?style=svg)](https://circleci.com/gh/monowai/beancounter)        

Services that transform transactions from, a Google Sheet, into a collection of portfolio positions which can optionally be valued against market data providers
    
*   [Import transactions](svc-import/README.md) from a Google Docs sheet
*   [Create positions](svc-position/README.md) from the transactions
*   Value positions using [Asset Prices](svc-md/README.md) obtained from the Market Data service  
    
No persistence exists so you need a connection to the Internet to read the trade file and value positions over the Internet    

Put together, the flow looks like this

```bash

# Create transactions
java -jar svc-import/build/libs/svc-import.jar \
    --beancounter.google.api=../secrets/google-api/credentials.json \
    --sheet=1a0EOYzNj4Ru2zGS76EQimzndjQm9URHQbuhwxvDLGJ8 \
    --out.file=./trades.json 

# Transform transactions to positions
curl -H "Content-Type: application/json" -X POST -d @trades.json <http://localhost:9500/> > positions.json

# Value positions    
curl -H "Content-Type: application/json" -X POST -d @positions.json <http://localhost:9500/value> > valuedPositions.json
```