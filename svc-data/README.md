# svc-data

## Features

- Persists core BeanCounter data.
- Retrieves asset prices via various providers such
  as [Alpha Advantage](https://www.alphavantage.co/documentation/).
- Converts market codes as necessary.
- Provides APIs to request prices for a single asset or a collection of assets.

You will need to register for an API key for each provider.
See the `application.yml` for how to set the key values.
Data providers can convert market codes as necessary

This service returns market data and asset transactions for portfolios.
You can request a prices for a single asset or a collection of them

```bash
curl -X GET http://localhost:9510/AX/AMP
```
