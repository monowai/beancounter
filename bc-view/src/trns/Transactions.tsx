import React from "react";
import "../css/styles.sass";
import { Link } from "react-router-dom";
import { getKey, useAssetTransactions } from "./hooks";
import ErrorPage from "../common/errors/ErrorPage";
import { useAsset } from "../assets/hooks";
import NumberFormat from "react-number-format";
import { isDone } from "../types/typeUtils";

export function Transactions(portfolioId: string, assetId: string): React.ReactElement {
  const trnsResult = useAssetTransactions(portfolioId, assetId);
  const assetResult = useAsset(assetId);

  if (isDone(trnsResult) && isDone(assetResult)) {
    if (assetResult.error) {
      return ErrorPage(assetResult.error.stack, assetResult.error.message);
    }
    if (trnsResult.error) {
      return ErrorPage(trnsResult.error.stack, trnsResult.error.message);
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
                    <th align={"right"}>Quantity</th>
                    <th align={"right"}>Price</th>
                    <th align={"right"}>Amount</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {trnsResult.data.map(t => (
                    <tr key={getKey(t.id)}>
                      <td>{t.trnType}</td>
                      <td>{t.tradeCurrency.code}</td>
                      <td>{t.tradeDate}</td>
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
                      <td align={"right"}>
                        <NumberFormat
                          value={t.tradeAmount}
                          displayType={"text"}
                          decimalScale={2}
                          fixedDecimalScale={true}
                          thousandSeparator={true}
                        />
                      </td>
                      <td>
                        <Link
                          className="fa fa-edit"
                          to={`/trns/${t.id.provider}/${t.id.batch}/${t.id.id}`}
                        />
                        <span> </span>
                        <Link
                          className="fa fa-remove has-padding-left-6"
                          to={`/portfolio/delete/${t.id}`}
                        />
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
  return <div id="root">Loading...</div>;
}

export default Transactions;
