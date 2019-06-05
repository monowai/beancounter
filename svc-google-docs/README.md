Simple google sheet reader adapted from [Google Java Quickstart](https://developers.google.com/sheets/api/quickstart/java)

   
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
    
Note that you should _never_ commit unencrypted credentials to a repo.  Hence, it is expected these are managed in a separate project.  You need
to pass your own Google credentials in the application property:

It is assumed that you are running all commands from the root of the `BeanCounter` project
Default is to look for google credentials `../secrets/credentials.json`  

```bash
java -jar svc-google-docs/build/libs/svc-google-docs.jar \
    --beancounter.google.api=../secrets/google-api/credentials.json \
    --sheet=1a0EOYzNj4Ru2zGS76EQimzndjQm9URHQbuhwxvDLGJ8 \
    --out.file=./trades.json 
```

You can pass an optional case sensitive filter with `--filter={CODE,CODE}` which will only include the transactions where the Asset codes match those in the filter