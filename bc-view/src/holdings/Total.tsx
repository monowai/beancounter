import { Holdings } from "../types/beancounter";
import React from "react";
import { FormatMoneyValue } from "../common/MoneyUtils";
import { ValueIn } from "../types/valueBy";

export default function Total(props: { holdings: Holdings; valueIn: ValueIn }): JSX.Element {
  const holdings = props.holdings;
  const valueIn = props.valueIn;
  return (
    <tbody className={"totals-row"} key={holdings.portfolio.code + "totals"}>
      <tr key={valueIn}>
        <td colSpan={3} align={"right"}>
          Totals in {valueIn} currency
        </td>
        <td align={"right"}>
          <FormatMoneyValue moneyValues={holdings.totals[valueIn]} moneyField={"costValue"} />
        </td>
        <td align={"right"}>
          <FormatMoneyValue moneyValues={holdings.totals[valueIn]} moneyField={"marketValue"} />
        </td>
        <td />
        <td align={"right"}>
          <FormatMoneyValue moneyValues={holdings.totals[valueIn]} moneyField={"dividends"} />
        </td>
        <td align={"right"}>
          <FormatMoneyValue moneyValues={holdings.totals[valueIn]} moneyField={"realisedGain"} />
        </td>
        <td align={"right"}>
          <FormatMoneyValue moneyValues={holdings.totals[valueIn]} moneyField={"unrealisedGain"} />
        </td>
        <td align={"right"}>
          <FormatMoneyValue moneyValues={holdings.totals[valueIn]} moneyField={"totalGain"} />
        </td>
      </tr>
    </tbody>
  );
}
