import React from "react";
import "../css/styles.sass";
import { Link } from "react-router-dom";
import { useAssetTransactions } from "./hooks";
import { ErrorPage } from "../errors/ErrorPage";
import { useAsset } from "../assets/hooks";
import NumberFormat from "react-number-format";
import { isDone } from "../types/typeUtils";
import { ShowError } from "../errors/ShowError";

export function Trades(portfolioId: string, assetId: string): React.ReactElement {
  const trnsResult = useAssetTransactions(portfolioId, assetId, "trades");
  const assetResult = useAsset(assetId);

  if (isDone(trnsResult) && isDone(assetResult)) {
    if (assetResult.error) {
      return ErrorPage(assetResult.error.stack, assetResult.error.message);
    }
    if (trnsResult.error) {
      return <ShowError error={trnsResult.error} />;
    }

    if (trnsResult.data.length > 0) {
      return (
        <div>
          <nav className="container">
            <div className={"page-title"}>
              <div className={"column page-title subtitle is-6"}>
                {assetResult.data.name}:{assetResult.data.market.code}
              </div>
            </div>
          </nav>
          <div className="page-box is-primary has-background-light">
            <div className="container">
              <table className={"table is-striped is-hoverable"}>
                <thead>
                  <tr>
                    <th>Type</th>
                    <th>Currency</th>
                    <th>Trade Date</th>
                    <th align={"right"}>Amount</th>
                    <th align={"right"}>Tax</th>
                    <th align={"right"}>Charges</th>
                    <th align={"right"}>Quantity</th>
                    <th align={"right"}>Price</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {trnsResult.data.map((t) => (
                    <tr key={t.id}>
                      <td>{t.trnType}</td>
                      <td>{t.tradeCurrency.code}</td>
                      <td>{t.tradeDate}</td>
                      <td align={"right"}>
                        <NumberFormat
                          value={t.tradeAmount}
                          displayType={"text"}
                          decimalScale={2}
                          fixedDecimalScale={true}
                          thousandSeparator={true}
                        />
                      </td>
                      <td align={"right"}>
                        <NumberFormat
                          value={t.tax}
                          displayType={"text"}
                          decimalScale={2}
                          fixedDecimalScale={true}
                          thousandSeparator={true}
                        />
                      </td>
                      <td align={"right"}>
                        <NumberFormat
                          value={t.fees}
                          displayType={"text"}
                          decimalScale={2}
                          fixedDecimalScale={true}
                          thousandSeparator={true}
                        />
                      </td>
                      <td align={"right"}>
                        <NumberFormat
                          value={t.quantity}
                          displayType={"text"}
                          decimalScale={0}
                          fixedDecimalScale={true}
                          thousandSeparator={true}
                        />
                      </td>
                      <td align={"right"}>
                        <NumberFormat
                          value={t.price}
                          displayType={"text"}
                          decimalScale={2}
                          fixedDecimalScale={true}
                          thousandSeparator={true}
                        />
                      </td>
                      <td>
                        <Link className="fa fa-edit" to={`/trns/${t.portfolio.id}/${t.id}`} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      );
    }
  }
  return (
    <div id="root" data-testid="loading">
      Loading...
    </div>
  );
}

export default Trades;
