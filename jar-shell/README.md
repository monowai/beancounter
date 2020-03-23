## Command line shell with data features

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

## General import flow from a google sheet
Currently callbacks don't work when running the shell in Docker
The general flow
```shell script
 # running the shell in DEV mode against the bc-demo stack
 # Assumes you've already registered you account via the UI or KeyCloak
 $ MARKETDATA_URL=http://localhost:9610/api java -jar jar-shell/build/libs/jar-shell-0.1.1.jar

 bc-shell$ login user@somewhere.com
 password: ********
 bc-shell$ register
 bc-shell$ add SGD "SGD Domiciled" SGD USD
 # This command will not work for you, so you need to setup your google access and a sheet before calling it
 # If you don't ingest transactions, then you will just see an empty portfolio in the viewer 
 bc-shell$ ingest --type GSHEET --file "1a0EOYzNj4Ru2zGS76EQimzndjQm9URHQbuhwxvDLGJ8" --portfolio SGD
```

Supports an optional case sensitive filter property `"filter": "MSFT,"APPL"` to include only transactions where the Asset codes match those in the filter
