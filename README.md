[![CircleCI](https://circleci.com/gh/monowai/beancounter.svg?style=svg)](https://circleci.com/gh/monowai/beancounter)        

Micro services that offer the capability of transforming transactions from a Google Sheet into a collection of portfolio positions
    
* [Create trades](svc-google-docs/README.md) from a Google Docs sheet
* [Create positions](svc-position/README.md) from the trades
* [Value positions](svc-md/README.md) using various price providers 
    
Currently no persistence is in place so you need a connection to the Internet to read the trade file and value positions over the Internet    

Put together, the flow looks like this

```bash

java -jar svc-google-docs/build/libs/svc-google-docs-0.0.1-SNAPSHOT.jar \
    --beancounter.google.api=../secrets/google-api/credentials.json \
    --sheet=1a0EOYzNj4Ru2zGS76EQimzndjQm9URHQbuhwxvDLGJ8 \
    --out.file=./trades.json 

curl -H "Content-Type: application/json" -X POST -d @trades.json http://localhost:9500/ > positions.json    
curl -H "Content-Type: application/json" -X POST -d @positions.json http://localhost:9500/value > valuedPositions.json

```

