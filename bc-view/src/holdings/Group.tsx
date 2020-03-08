import React from "react";
import NumberFormat from "react-number-format";
import { HoldingGroup } from "../types/beancounter";

import { translate } from "../common/i18nConfig";
import { FormatMoneyValue } from "../common/MoneyUtils";
import { ValueIn } from "../types/valueBy";

export function HoldingHeader(props: { groupKey: string }): JSX.Element {
  return (
    <tbody className={"table-header"}>
      <tr>
        <th>{props.groupKey}</th>
        <th align={"right"}>{translate("price")}</th>
        <th align={"right"}>{translate("quantity")}</th>
        <th align={"right"}>{translate("value")}</th>
        <th align={"right"}>{translate("cost")}</th>
        <th align={"right"}>{translate("cost.avg")}</th>
        <th align={"right"}>{translate("dividends")}</th>
        <th align={"right"}>{translate("gain.realised")}</th>
        <th align={"right"}>{translate("gain.unrealised")}</th>
        <th align={"right"}>{translate("gain")}</th>
      </tr>
    </tbody>
  );
}

export function HoldingRows(props: { holdingGroup: HoldingGroup; valueIn: ValueIn }): JSX.Element {
  const valueIn = props.valueIn;
  // eslint-disable-next-line complexity
  const holdings = props.holdingGroup.positions.map((position, index) => (
    <tr key={props.holdingGroup.group + index} className={"holding-row"}>
      <td className={"asset"}>{position.asset.code + ": " + position.asset.name}</td>
      <td align={"right"}>
        {!position.asset.market.currency ? (
          "-"
        ) : (
          <label>
            {position.moneyValues[valueIn].currency.id}
            {position.moneyValues[valueIn].currency.symbol}
            &nbsp;
            <NumberFormat
              value={position.moneyValues[valueIn].price}
              displayType={"text"}
              decimalScale={2}
              fixedDecimalScale={true}
              thousandSeparator={true}
            />
          </label>
        )}
      </td>
      <td align={"right"}>
        <NumberFormat
          value={position.quantityValues.total}
          displayType={"text"}
          decimalScale={0}
          fixedDecimalScale={true}
          thousandSeparator={true}
        />
      </td>
      <td align={"right"}>
        <FormatMoneyValue moneyValues={position.moneyValues[valueIn]} moneyField={"marketValue"} />
      </td>
      <td align={"right"}>
        <FormatMoneyValue moneyValues={position.moneyValues[valueIn]} moneyField={"costValue"} />
      </td>
      <td align={"right"}>
        <FormatMoneyValue moneyValues={position.moneyValues[valueIn]} moneyField={"averageCost"} />
      </td>
      <td align={"right"}>
        <FormatMoneyValue moneyValues={position.moneyValues[valueIn]} moneyField={"dividends"} />
      </td>
      <td align={"right"}>
        <FormatMoneyValue moneyValues={position.moneyValues[valueIn]} moneyField={"realisedGain"} />
      </td>
      <td align={"right"}>
        <FormatMoneyValue
          moneyValues={position.moneyValues[valueIn]}
          moneyField={"unrealisedGain"}
        />
      </td>
      <td align={"right"}>
        <FormatMoneyValue moneyValues={position.moneyValues[valueIn]} moneyField={"totalGain"} />
      </td>
    </tr>
  ));
  return <tbody>{holdings}</tbody>;
}

export function HoldingFooter(props: {
  holdingGroup: HoldingGroup;
  valueIn: ValueIn;
}): JSX.Element {
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
            moneyField={"costValue"}
          />
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
