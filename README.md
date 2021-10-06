[![CircleCI](https://circleci.com/gh/monowai/beancounter.svg?style=svg)](https://circleci.com/gh/monowai/beancounter)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/2bfdd3f89fbc47b0b9d8920fe094ccd9)](https://www.codacy.com/manual/monowai/beancounter?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=monowai/beancounter&amp;utm_campaign=Badge_Grade)
[![Codacy Badge](https://app.codacy.com/project/badge/Coverage/2bfdd3f89fbc47b0b9d8920fe094ccd9)](https://www.codacy.com/gh/monowai/beancounter/dashboard?utm_source=github.com&utm_medium=referral&utm_content=monowai/beancounter&utm_campaign=Badge_Coverage)

## Financial Transaction Processing Services

Transform financial transaction data into portfolio positions which can then be valued against market data 

There's a demo stack connected with KeyCloak and Postgres [over here](http://github.com/monowai/bc-demo)

*   [Viewer](https://github.com/monowai/bc-view) login, create portfolios and view positions
*   [Data Services](svc-data/README.md) Market data, portfolios, transactions, FX rates etc
*   [Position Service](svc-position/README.md) Compute positions from the Data Services
*   [Corporate Events](svc-event/README.md) for handling of corporate events
