[![CircleCI](https://circleci.com/gh/monowai/beancounter.svg?style=svg)](https://circleci.com/gh/monowai/beancounter)        

Services to chain together capability capable of valuing a series of transactions from a Google Sheet
    
    Read a Google Docs sheet
    Pass normalised transactions to the svc-position
    Computes various portfolio valuation attributes
    
    ToDo: Plug in price providers to the Market Data service 
               
Currently no persistence is in place

```bash
# Read the transactions and create a trades.json file
java -jar -Dspring.profiles.active=trade svc-google-docs/build/libs/svc-google-docs-0.0.1-SNAPSHOT.jar \
    --beancounter.google.api=../secrets/google-api/credentials.json \
    --sheet=1a0EOYzNj4Ru2zGS76EQimzndjQm9URHQbuhwxvDLGJ8 \
    --out.file=./trades.json 

# Push trades.json into svc-positions to create an accumulated view
curl -H "Content-Type: application/json" -X POST -d @trades.json http://localhost:9500/ > positions.json    curl -H "Content-Type: application/json" -X POST -d @trades.json http://localhost:9500/ > positions.json
curl -H "Content-Type: application/json" -X POST -d @positions.json http://localhost:9500/value > valuedPositions.json

```

