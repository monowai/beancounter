import React from "react";
import NumberFormat from "react-number-format";
import { HoldingGroup } from "../types/beancounter";

import { translate } from "../i18nConfig";
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

export function HoldingRows(props: {
  holdingGroup: HoldingGroup;
  valueIn: ValueIn;
}): JSX.Element {
  const valueIn = props.valueIn;
  // eslint-disable-next-line complexity
  const holdings = props.holdingGroup.positions.map((position, index) => (
    <tr key={props.holdingGroup.group + index} className={"holding-row"}>
      <td className={"asset"}>
        {position.asset.code + ": " + position.asset.name}
      </td>
      <td>
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
      <td>
        <NumberFormat
          value={position.quantityValues.total}
          displayType={"text"}
          decimalScale={0}
          fixedDecimalScale={true}
          thousandSeparator={true}
        />
      </td>
      <FormatMoneyValue
        moneyValues={position.moneyValues[valueIn]}
        moneyField={"marketValue"}
      />
      <FormatMoneyValue
        moneyValues={position.moneyValues[valueIn]}
        moneyField={"costValue"}
      />
      <FormatMoneyValue
        moneyValues={position.moneyValues[valueIn]}
        moneyField={"averageCost"}
      />
      <FormatMoneyValue
        moneyValues={position.moneyValues[valueIn]}
        moneyField={"dividends"}
      />
      <FormatMoneyValue
        moneyValues={position.moneyValues[valueIn]}
        moneyField={"realisedGain"}
      />
      <FormatMoneyValue
        moneyValues={position.moneyValues[valueIn]}
        moneyField={"unrealisedGain"}
      />
      <FormatMoneyValue
        moneyValues={position.moneyValues[valueIn]}
        moneyField={"totalGain"}
      />
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
        <FormatMoneyValue
          moneyValues={holdingGroup.subTotals[valueIn]}
          moneyField={"costValue"}
        />
        <FormatMoneyValue
          moneyValues={holdingGroup.subTotals[valueIn]}
          moneyField={"marketValue"}
        />
        <FormatMoneyValue
          moneyValues={holdingGroup.subTotals[valueIn]}
          moneyField={"dividends"}
        />
        <FormatMoneyValue
          moneyValues={holdingGroup.subTotals[valueIn]}
          moneyField={"realisedGain"}
        />
        <FormatMoneyValue
          moneyValues={holdingGroup.subTotals[valueIn]}
          moneyField={"unrealisedGain"}
        />
        <FormatMoneyValue
          moneyValues={holdingGroup.subTotals[valueIn]}
          moneyField={"totalGain"}
        />
      </tr>
    </tbody>
  );
}
