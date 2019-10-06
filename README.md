
[![CircleCI](https://circleci.com/gh/monowai/beancounter.svg?style=svg)](https://circleci.com/gh/monowai/beancounter)   [![Codacy Badge](https://api.codacy.com/project/badge/Grade/2bfdd3f89fbc47b0b9d8920fe094ccd9)](https://www.codacy.com/manual/monowai/beancounter?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=monowai/beancounter&amp;utm_campaign=Badge_Grade)

## Financial Transaction Processing Services

Transform financial transaction data into portfolio positions which can then be valued against market data 
    
*   [Ingest transactions](svc-ingest/README.md) from a Google Docs sheet
*   [Create positions](svc-position/README.md) from the transactions
*   Value positions using [Asset Prices](svc-md/README.md) obtained from the Market Data service  
    
No persistence exists so you need a connection to the Internet to read the trade file and value positions over the Internet    

Put together, the flow looks like this

```bash

# Create transactions
java -jar svc-ingest/build/libs/svc-ingest-0.1.1.jar \
    --beancounter.google.api=../secrets/google-api/credentials.json \
    --sheet=1a0EOYzNj4Ru2zGS76EQimzndjQm9URHQbuhwxvDLGJ8 \
    --out.file=./trades.json 

# Transform transactions to positions
curl -H "Content-Type: application/json" -X POST -d @trades.json <http://localhost:9500/api> > positions.json

# Value positions    
curl -H "Content-Type: application/json" -X POST -d @positions.json <http://localhost:9500/api/value> > valuedPositions.json
```

