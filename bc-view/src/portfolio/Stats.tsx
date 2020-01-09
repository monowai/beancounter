import React from "react";
import "../assets/styles.sass";
import { translate } from "../i18nConfig";
import { MoneyValues, Portfolio } from "../types/beancounter";
import { FormatMoneyValue } from "../common/MoneyUtils";
import { ValueIn } from "../types/valueBy";

export default function StatsHeader(props: { portfolio: Portfolio }): JSX.Element {
  return (
    <tbody key={props.portfolio.code}>
      <tr className={"stats-header"}>
        <th align={"left"}>Summary</th>
        <th>{translate("dividends")}</th>
        <th>{translate("purchases")}</th>
        <th>{translate("sales")}</th>
        <th>{translate("value")}</th>
        <th>{translate("strategy")}</th>
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
            {valueIn === ValueIn.TRADE ? "N/A" : moneyValues.currency.code}
          </div>
        </td>
        <FormatMoneyValue moneyValues={moneyValues} moneyField={"dividends"} />
        <FormatMoneyValue moneyValues={moneyValues} moneyField={"purchases"} />
        <FormatMoneyValue moneyValues={moneyValues} moneyField={"sales"} />
        <FormatMoneyValue moneyValues={moneyValues} moneyField={"marketValue"} />
        <FormatMoneyValue moneyValues={moneyValues} moneyField={"totalGain"} />
      </tr>
    </tbody>
  );
}
