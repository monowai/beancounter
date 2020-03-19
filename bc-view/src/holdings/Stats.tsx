import React from "react";
import "../css/styles.sass";
import { translate } from "../common/i18nConfig";
import { MoneyValues, Portfolio } from "../types/beancounter";
import { FormatMoneyValue } from "../common/MoneyUtils";
import { ValueIn } from "../types/valueBy";

export default function StatsHeader(props: { portfolio: Portfolio }): JSX.Element {
  return (
    <tbody key={props.portfolio.code}>
      <tr className={"stats-header"}>
        <th align={"left"}>Summary</th>
        <th align={"right"}>{translate("dividends")}</th>
        <th align={"right"}>{translate("purchases")}</th>
        <th align={"right"}>{translate("sales")}</th>
        <th align={"right"}>{translate("value")}</th>
        <th align={"right"}>{translate("strategy")}</th>
      </tr>
    </tbody>
  );
}

export function StatsRow(props: {
  portfolio: Portfolio;
  moneyValues: MoneyValues[];
  valueIn: ValueIn;
}): JSX.Element {
  const portfolio = props.portfolio;
  const valueIn = props.valueIn;
  const moneyValues = props.moneyValues[valueIn];
  return (
    <tbody>
      <tr className={"stats-row"}>
        <td>
          <div className="left-cell">
            {portfolio.code.toUpperCase()}:{" "}
            {!moneyValues || valueIn === ValueIn.TRADE ? "N/A" : moneyValues.currency.code}
          </div>
        </td>
        <td align={"right"}>
          <FormatMoneyValue moneyValues={moneyValues} moneyField={"dividends"} />
        </td>
        <td align={"right"}>
          <FormatMoneyValue moneyValues={moneyValues} moneyField={"purchases"} />
        </td>
        <td align={"right"}>
          <FormatMoneyValue moneyValues={moneyValues} moneyField={"sales"} />
        </td>
        <td align={"right"}>
          <FormatMoneyValue moneyValues={moneyValues} moneyField={"marketValue"} />
        </td>
        <td align={"right"}>
          <FormatMoneyValue moneyValues={moneyValues} moneyField={"totalGain"} />
        </td>
      </tr>
    </tbody>
  );
}
