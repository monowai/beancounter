import { Holdings } from "../types/beancounter";
import React from "react";
import { FormatNumber } from "../common/MoneyUtils";
import { ValueIn } from "../types/valueBy";

export default function Total(props: { holdings: Holdings; valueIn: ValueIn }): JSX.Element {
  const holdings = props.holdings;
  const valueIn = props.valueIn;
  return (
    <tbody className={"totals-row"} key={holdings.portfolio.code + "totals"}>
      <tr key={valueIn}>
        <td colSpan={4} align={"right"}>
          Totals in {valueIn} currency
        </td>
        <td align={"right"}>
          <FormatNumber values={holdings.totals[valueIn]} field={"marketValue"} />
        </td>
        <td align={"right"}>
          <FormatNumber values={holdings.totals[valueIn]} field={"unrealisedGain"} />
        </td>
        <td />
        <td align={"right"}>
          <FormatNumber values={holdings.totals[valueIn]} field={"costValue"} />
        </td>
        <td />
        <td align={"right"}>
          <FormatNumber values={holdings.totals[valueIn]} field={"dividends"} />
        </td>
        <td align={"right"}>
          <FormatNumber values={holdings.totals[valueIn]} field={"realisedGain"} />
        </td>
        <td align={"right"}>
          <FormatNumber values={holdings.totals[valueIn]} field={"totalGain"} />
        </td>
      </tr>
    </tbody>
  );
}
