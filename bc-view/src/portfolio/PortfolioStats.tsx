import React from "react";
import "../assets/styles.sass";
import { translate } from "../i18nConfig";
import NumberFormat from "react-number-format";
import { MoneyValues, Portfolio } from "../types/beancounter";

export default function PortfolioStats(portfolio: Portfolio): JSX.Element {
  return (
    <tbody className={"table-container"} key={portfolio.code}>
      <tr className={"stats-row"}>
        <th className={"stats-header"}>
          {portfolio.code}: Summary - {portfolio.currency.code}
        </th>
        <th className={"stats-header"} align={"right"}>
          {translate("dividends")}
        </th>
        <th className={"stats-header"} align={"right"}>
          {translate("value")}
        </th>
        <th className={"stats-header"} align={"right"}>
          {translate("purchases")}
        </th>
        <th className={"stats-header"} align={"right"}>
          {translate("sales")}
        </th>
        <th className={"stats-header"} align={"right"}>
          {translate("strategy")}
        </th>
      </tr>
    </tbody>
  );
}

export function Stats(moneyValues: MoneyValues): JSX.Element {
  return (
    <tbody>
      <tr>
        <td />
        <td align={"right"}>
          <NumberFormat
            value={moneyValues.dividends}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </td>
        <td align={"right"}>
          <NumberFormat
            value={moneyValues.marketValue}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </td>
        <td align={"right"}>
          <NumberFormat
            value={moneyValues.purchases}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </td>
        <td align={"right"}>
          <NumberFormat
            value={moneyValues.sales}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </td>
        <td align={"right"}>
          <NumberFormat
            value={
              moneyValues.purchases -
              moneyValues.sales +
              moneyValues.dividends +
              moneyValues.marketValue
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
