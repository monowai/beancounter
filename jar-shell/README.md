## Command line shell with data features

Data import reader using CSV.

Converts rows in a CSV file to BeanCounter trade format.  

Exchange rates are back filled based on your portfolio currency and common reference currency. This behaviour can be overridden

All trades in a single sheet.  The first row is skipped as it's assumed to be a header.  Headers are included for clarity

```csv
# Trade Format
Market	Code	Name	    Type	Date	    Quantity	Price *	Brokerage *	Currency	Exchange Rate	Value	    Comments
NASDAQ,INTC,Intel Corp,Buy,30/05/2019,80,25.83,4.42,USD,,2,646.08

#Dividend Format - if including trades and divis in the same file then do not include the header 
Code	    Name	    Date Paid	Exchange Rate	Currency	Net Amount	Foreign Tax Deducted	Gross Amount	Comments
INTC.NAS,Intel Corp,15/02/2013,,USD,15.85,0.00,15.85,Dividend of 40.0 cents per share
```
    
It is assumed that you are running all commands from the root of the `BeanCounter` project

## General import flow from a CSV
Ingestion flow against the BC_DEMO stack.  Assumes you've already registered and successfully logged in @ <http://localhost:4000>
```shell script
 # running the shell in DEV mode against the bc-demo stack

 $ MARKETDATA_URL=http://localhost:9610/api java -jar jar-shell/build/libs/jar-shell-0.1.1.jar

 bc-shell$ login user@somewhere.com
 password: ********
 # Assumes you've already registered you account via the UI or KeyCloak
 # If you get an error saying `Unable to identify the owner`, then simply type `register`
 # Create a portfolio if you didn't create one in bc-view
 bc-shell$ add SGD "SGD Domiciled" SGD USD
 # If you don't ingest transactions, then you will just see an empty portfolio in the viewer
 bc-shell$ ingest KAFKA jar-shell/src/test/resources/trades.csv SGD
```
