import React from "react";
import "../css/styles.sass";
import { MoneyValues, Portfolio } from "../types/beancounter";
import { FormatNumber } from "../common/MoneyUtils";
import { ValueIn } from "../types/valueBy";
import { Link } from "react-router-dom";
import { translate } from "../common/i18nUtils";

export default function StatsHeader(props: { portfolio: Portfolio }): JSX.Element {
  return (
    <tbody key={props.portfolio.code}>
      <tr className={"stats-header"}>
        <th align={"left"}>Summary</th>
        <th align={"right"}>{translate("value")}</th>
        <th align={"right"}>{translate("purchases")}</th>
        <th align={"right"}>{translate("sales")}</th>
        <th align={"right"}>{translate("dividends")}</th>
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
            <Link to={`/portfolios/${portfolio.id}`}>
              <span className={"has-tooltip-right"} data-tooltip={portfolio.name}>
                {portfolio.code.toUpperCase()} {": "}
              </span>
            </Link>
            {!moneyValues || valueIn === ValueIn.TRADE ? "N/A" : moneyValues.currency.code}
          </div>
        </td>
        <td align={"right"}>
          <FormatNumber values={moneyValues} field={"marketValue"} />
        </td>
        <td align={"right"}>
          <FormatNumber values={moneyValues} field={"purchases"} />
        </td>
        <td align={"right"}>
          <FormatNumber values={moneyValues} field={"sales"} />
        </td>
        <td align={"right"}>
          <FormatNumber values={moneyValues} field={"dividends"} />
        </td>
        <td align={"right"}>
          <FormatNumber values={moneyValues} field={"totalGain"} />
        </td>
      </tr>
    </tbody>
  );
}
