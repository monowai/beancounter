import React from "react";
import { HoldingGroup } from "../types/beancounter";
import { FormatMoneyValue } from "../common/MoneyUtils";
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
          <FormatMoneyValue
            moneyValues={holdingGroup.subTotals[valueIn]}
            moneyField={"marketValue"}
          />
        </td>
        <td align={"right"}>
          <FormatMoneyValue
            moneyValues={holdingGroup.subTotals[valueIn]}
            moneyField={"costValue"}
          />
        </td>
        <td />
        <td align={"right"}>
          <FormatMoneyValue
            moneyValues={holdingGroup.subTotals[valueIn]}
            moneyField={"dividends"}
          />
        </td>
        <td align={"right"}>
          <FormatMoneyValue
            moneyValues={holdingGroup.subTotals[valueIn]}
            moneyField={"realisedGain"}
          />
        </td>
        <td align={"right"}>
          <FormatMoneyValue
            moneyValues={holdingGroup.subTotals[valueIn]}
            moneyField={"unrealisedGain"}
          />
        </td>
        <td align={"right"}>
          <FormatMoneyValue
            moneyValues={holdingGroup.subTotals[valueIn]}
            moneyField={"totalGain"}
          />
        </td>
      </tr>
    </tbody>
  );
}
