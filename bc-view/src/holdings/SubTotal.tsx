import React from "react";
import { HoldingGroup } from "../types/beancounter";
import { FormatNumber } from "../common/MoneyUtils";
import { ValueIn } from "../types/valueBy";

export function SubTotal(props: { holdingGroup: HoldingGroup; valueIn: ValueIn }): JSX.Element {
  const valueIn = props.valueIn;
  const holdingGroup = props.holdingGroup;
  return (
    <tbody className={"holding-totals-row"}>
      <tr key={holdingGroup.group} className={"holding-footer"}>
        <td colSpan={4} align={"right"}>
          Sub-Total - {holdingGroup.subTotals[valueIn].currency.code}
        </td>
        <td align={"right"}>
          <FormatNumber values={holdingGroup.subTotals[valueIn]} field={"marketValue"} />
        </td>
        <td align={"right"}>
          <FormatNumber values={holdingGroup.subTotals[valueIn]} field={"unrealisedGain"} />
        </td>
        <td />
        <td align={"right"}>
          <FormatNumber values={holdingGroup.subTotals[valueIn]} field={"costValue"} />
        </td>
        <td />
        <td align={"right"}>
          <FormatNumber values={holdingGroup.subTotals[valueIn]} field={"dividends"} />
        </td>
        <td align={"right"}>
          <FormatNumber values={holdingGroup.subTotals[valueIn]} field={"realisedGain"} />
        </td>
        <td align={"right"}>
          <FormatNumber values={holdingGroup.subTotals[valueIn]} field={"totalGain"} />
        </td>
      </tr>
    </tbody>
  );
}
