import React from "react";
import "../assets/styles.sass";
import { translate } from "../i18nConfig";
import NumberFormat from "react-number-format";
import { MoneyValues, Portfolio } from "../types/beancounter";

export default function PortfolioStats(portfolio: Portfolio): JSX.Element {
  return (
    <tbody key={portfolio.code}>
      <tr className={"stats-header"}>
        <th align={"left"}>Summary</th>
        <th>{translate("dividends")}</th>
        <th>{translate("value")}</th>
        <th>{translate("purchases")}</th>
        <th>{translate("sales")}</th>
        <th>{translate("strategy")}</th>
      </tr>
    </tbody>
  );
}

export function Stats(
  portfolio: Portfolio,
  moneyValues: MoneyValues
): JSX.Element {
  return (
    <tbody>
      <tr className={"stats-row"}>
        <td>
          <div className="left-cell">
            {portfolio.code.toUpperCase()}: {portfolio.currency.code}
          </div>
        </td>
        <td>
          <NumberFormat
            value={moneyValues.dividends}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </td>
        <td>
          <NumberFormat
            value={moneyValues.marketValue}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </td>
        <td>
          <NumberFormat
            value={moneyValues.purchases}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </td>
        <td>
          <NumberFormat
            value={moneyValues.sales}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </td>
        <td>
          <NumberFormat
            value={
              moneyValues.marketValue -
              moneyValues.purchases +
              moneyValues.sales +
              moneyValues.dividends
            }
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </td>
      </tr>
    </tbody>
  );
}
