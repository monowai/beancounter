A hackabout project to evaluate various technologies in the area of micro services and resilience 

Being that it's always useful to have a functional goal, it has a chain of services that:
    
    Read a Google Docs sheet
        Create a .json file of Transaction objects
    Passes the .json file to bc-position
        Computes the portfolio positions
        Values positions against bc-md
        
there's a bunch of functionality that will be added to over time.  

bc-md is the Market Data pricing service.  

Currently no persistence is in place          