## Data Ingestion Features

Data import reader supporting google sheet. Adapted from [Google Java Quickstart](https://developers.google.com/sheets/api/quickstart/java)

Converts rows in a Google Sheet and writes a JSON file with BeanCounter trades that can be passed to other services.  
These formats are going to evolve, but at the moment, then work for Trades and Dividends using the following structures:

You can put all trades in a single sheet.  The `range` is defined as "All Trades Report"

```csv
# ToDo: tidy up the formatting ;-)
# Trade Format
Market	Code	Name	    Type	Date	    Quantity	Price *	Brokerage *	Currency	Exchange Rate	Value	    Comments
NASDAQ	INTC	Intel Corp	Buy	    30/05/2012	80	        25.83	4.42	    USD	        0.7826	        2,646.08

#Dividend Format
Code	    Name	    Date Paid	Exchange Rate	Currency	Net Amount	Foreign Tax Deducted	Gross Amount	Comments
ABBV.NYS    AbbVie Inc      15/02/2013	0.8074  USD	        15.85	    0.00	             15.85	        Dividend of 40.0 cents per share
```
    
It is assumed that you are running all commands from the root of the `BeanCounter` project
Default is to look for google credentials `../secrets/credentials.json`  

## ShareSight transformer
passing `ratesIgnored`, the transformer will normalise the fees into trade currency terms using the input rate, but will re-retrieve the trade rate from the market data service.  Default is false, use the rate supplied by SS
```bash
curl -X POST \
  http://localhost:9520/api/ \
  -H 'Content-Type: application/json' \
  -d '{
  "sheetId":"1a0EOYzNj4Ru2zGS76EQimzndjQm9URHQbuhwxvDLGJ8",
  "ratesIgnored": true,
  "portfolio": {
  	"code": "mike",
  	"currency": { "code": "USD"},
  	"base": { "code": "NZD"}
  }
}' \
-o trades.json 
```

You can pass an optional case sensitive filter property `"filter": "MSFT,"APPL"` which will only include the transactions where the Asset codes match those in the filter
 