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

Default is to look for `../secrets/credentials.json` which is assuming the root of `BeanCounter` is where you are running from.  

```bash
# Assumes you are running from the build/libs folder
java -jar -Dspring.profiles.active=trade svc-google-docs-0.0.1-SNAPSHOT.jar  --sheet=1FmWWSw956mD31Nz4cRkGv1UrCJ-tuSM8BnEuAUSXRsE --com.beancounter.google.api=../../../../secrets/google-api/credentials.json 

```