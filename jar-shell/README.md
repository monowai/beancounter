## Command line shell with data features

Data import reader using CSV.

Convert CSV rows to BeanCounter trade format.  

Exchange rates are calculated if not provided.  TradeAmount is Qty * Price - Fees if not supplied and should be in tradeCurrency.  

TradeCurrency defaults to USD if not supplied

First row is always skipped as it's assumed to be a header.  Headers are included for clarity
Use # to indicate a comment
Columns shown with an * are optional and will be calculated by BeanCounter

```csv
Trades:
provider*,batch*,callerId*,type,market,code,name*,date,quantity,tradeCurrency*,price,fees,portfolioRate*,tradeAmount*,comments*
BC,batch,1,BUY,NASDAQ,QCOM,QCOM,2021-03-28,10.000000,USD,5.990000,1.00,null,60.90,
BC,batch,2,BUY,NASDAQ,TREX,TREX,2021-03-28,10.000000,USD,5.990000,1.00,null,60.90,

Dividends - TranAmount is the value in tradeCurrency 
provider*,batch*,callerId*,type,market,code,name*,date,quantity,tradeCurrency*,price*,fees*,portfolioRate*,tradeAmount,comments*
Owq3jmXaRgu9S_O8DZrIpQ,USX,21,DIVI,NYSE,AVLR,Avalara Inc.,2019-11-12,0.000000,USD,null,0.00,1.283500,20.18,share
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
 bc-shell$ ingest --file jar-shell/src/test/resources/trades.csv SGD
```
