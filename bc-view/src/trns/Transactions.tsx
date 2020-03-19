import React from "react";
import "../css/styles.sass";
import { Link } from "react-router-dom";
import { getKey, useAssetTransactions } from "./hooks";
import ErrorPage from "../common/errors/ErrorPage";
import { useAsset } from "../assets/hooks";
import NumberFormat from "react-number-format";

export function Transactions(portfolioId: string, assetId: string): React.ReactElement {
  const [transactions, trnError] = useAssetTransactions(portfolioId, assetId);
  const [asset, assetError] = useAsset(assetId);

  // Render where we are in the initialization process
  if (!transactions && !asset) {
    return <div id="root">Loading...</div>;
  }
  if (trnError) {
    return ErrorPage(trnError.stack, trnError.message);
  }
  if (assetError) {
    return ErrorPage(assetError.stack, assetError.message);
  }

  if (transactions && asset) {
    if (transactions.length > 0) {
      return (
        <div>
          <nav className="container">
            <div className={"page-title"}>
              <div className={"column page-title subtitle is-6"}>
                {asset?.name}:{asset?.market.code}
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
                  {transactions.map(transaction => (
                    <tr key={getKey(transaction.id)}>
                      <td>{transaction.trnType}</td>
                      <td>{transaction.tradeCurrency.code}</td>
                      <td>{transaction.tradeDate}</td>
                      <td align={"right"}>
                        <NumberFormat
                          value={transaction.quantity}
                          displayType={"text"}
                          decimalScale={0}
                          fixedDecimalScale={true}
                          thousandSeparator={true}
                        />
                      </td>
                      <td align={"right"}>
                        <NumberFormat
                          value={transaction.price}
                          displayType={"text"}
                          decimalScale={2}
                          fixedDecimalScale={true}
                          thousandSeparator={true}
                        />
                      </td>
                      <td align={"right"}>
                        <NumberFormat
                          value={transaction.tradeAmount}
                          displayType={"text"}
                          decimalScale={2}
                          fixedDecimalScale={true}
                          thousandSeparator={true}
                        />
                      </td>
                      <td>
                        <Link className="fa fa-edit" to={`/portfolio/${transaction.id}`} />
                        <span> </span>
                        <Link
                          className="fa fa-remove has-padding-left-6"
                          to={`/portfolio/delete/${transaction.id}`}
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
  return (
    <div id="root">
      You have no transactions for this portfolio - <Link to={"/portfolio/new"}>create one?</Link>
    </div>
  );
}

export default Transactions;
