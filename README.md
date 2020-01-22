
[![CircleCI](https://circleci.com/gh/monowai/beancounter.svg?style=svg)](https://circleci.com/gh/monowai/beancounter) [![codecov](https://codecov.io/gh/monowai/beancounter/branch/master/graph/badge.svg)](https://codecov.io/gh/monowai/beancounter) [![Codacy Badge](https://api.codacy.com/project/badge/Grade/2bfdd3f89fbc47b0b9d8920fe094ccd9)](https://www.codacy.com/manual/monowai/beancounter?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=monowai/beancounter&amp;utm_campaign=Badge_Grade)[![Known Vulnerabilities](https://snyk.io/test/github/monowai/beancounter/badge.svg)](https://snyk.io/test/github/monowai/beancounter)

## Financial Transaction Processing Services

Transform financial transaction data into portfolio positions which can then be valued against market data 
    
*   [Ingest transactions](jar-shell/README.md) from a Google Docs sheet
*   [Create positions](svc-position/README.md) from the transactions
*   Value positions with [Asset Prices](svc-data/README.md) from the BC Data service  
    
Put together, the flow looks like this

```bash
# Create the portfolio
curl -X POST \
  http://localhost:9510/api/portfolios \
  -H 'Content-Type: application/json' \
  -d '{
  "data": [
    {
      "id": "FT-vUCChRwOXDP7itcp5Kw",
      "code": "TEST",
      "name": "NZD Portfolio",
      "currency": {
        "code": "NZD"
      },
      "base": {
        "code": "USD"
      }
    }
  ]
}'

# Portfolio Code to load the transactions against

# Transform columnar data into transaction objects
curl -X POST \
  http://localhost:9520/api/ \
  -H 'Content-Type: application/json' \
  -d '{
  "sheetId":"1a0EOYzNj4Ru2zGS76EQimzndjQm9URHQbuhwxvDLGJ8",
  "provider": "SHEETS",
  "portfolioCode": "TEST"
}' -o trades.json 

# Rollup transactions into positions
curl -H "Content-Type: application/json" -X POST -d @trades.json http://localhost:9500/api/ > positions.json

# Value positions with market data    
curl -H "Content-Type: application/json" -X POST -d @positions.json http://localhost:9500/api/value > valuedPositions.json
```

