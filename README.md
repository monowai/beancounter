[![CircleCI](https://circleci.com/gh/monowai/beancounter.svg?style=svg)](https://circleci.com/gh/monowai/beancounter)        

Services to chain together capability capable of valuing a series of transactions from a Google Sheet
    
    Read a Google Docs sheet
    Pass normalised transactions to the svc-position
    Computes various portfolio valuation attributes
    
    ToDo: Plug in price providers to the Market Data service 
               
Currently no persistence is in place

curl -H "Content-Type: application/json" -X POST -d @trades.json http://localhost:9500/    

