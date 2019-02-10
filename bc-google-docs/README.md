Simple google sheet reader adapted from [Google Java Quickstart](https://developers.google.com/sheets/api/quickstart/java)

you can pass in the data range to read via the property 
    `com.beanconter.source.sheet.range` 
   
and the sheet id 
    `com.beanconter.source.sheet.id`
    
  
It performs the role of reading rows and converting the input into a bc-common:Transaction which it outputs to a .json file 


Note that you should _never_ commit unencrypted credentials to a repo.  Hence, these are kept in a separate project.  You need
to pass your own Google credentials in the application property:

```
com.beancounter.google.api 
```

Otherwise it will simply default to looking for `credentials.json`.  I keep mine at `../secrets/google-api`