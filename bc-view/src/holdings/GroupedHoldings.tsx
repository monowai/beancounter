import React from "react";
import NumberFormat from "react-number-format";
import { HoldingGroup } from "../types/beancounter";
import { ValuationCcy } from "./groupBy";

import { translate } from "../i18nConfig";

export function HoldingHeader(groupKey: string): JSX.Element {
  return (
    <tbody className={"table-header"}>
      <tr>
        <th>{groupKey}</th>
        <th align={"right"}>{translate("quantity")}</th>
        <th align={"right"}>{translate("price")}</th>
        <th align={"right"}>{translate("cost.avg")}</th>
        <th align={"right"}>{translate("cost")}</th>
        <th align={"right"}>{translate("value")}</th>
        <th align={"right"}>{translate("dividends")}</th>
        <th align={"right"}>{translate("gain.realised")}</th>
        <th align={"right"}>{translate("gain.unrealised")}</th>
        <th align={"right"}>{translate("gain")}</th>
      </tr>
    </tbody>
  );
}

export function WriteHoldings(
  holdingGroup: HoldingGroup,
  valueIn: ValuationCcy
): JSX.Element {
  // eslint-disable-next-line complexity
  const holdings = holdingGroup.positions.map((position, index) => (
    <tr key={holdingGroup.group + index} className={"holding-row"}>
      <td className={"asset"}>
        {position.asset.code + ": " + position.asset.name}
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
        {!position.moneyValues ? (
          "-"
        ) : (
          <NumberFormat
            value={position.moneyValues[valueIn].averageCost}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        )}
      </td>
      <td>
        {!position.moneyValues ? (
          "-"
        ) : (
          <NumberFormat
            value={position.moneyValues[valueIn].costValue}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        )}
      </td>

      <td>
        {!position.moneyValues[valueIn] ? (
          "-"
        ) : (
          <NumberFormat
            value={position.moneyValues[valueIn].marketValue}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        )}
      </td>
      <td>
        <NumberFormat
          value={position.moneyValues[valueIn].dividends}
          displayType={"text"}
          decimalScale={2}
          fixedDecimalScale={true}
          thousandSeparator={true}
        />
      </td>
      <td>
        <NumberFormat
          value={position.moneyValues[valueIn].realisedGain}
          displayType={"text"}
          decimalScale={2}
          fixedDecimalScale={true}
          thousandSeparator={true}
        />
      </td>
      <td>
        {!position.moneyValues[valueIn] ? (
          "-"
        ) : (
          <NumberFormat
            value={position.moneyValues[valueIn].unrealisedGain}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        )}
      </td>
      <td>
        <NumberFormat
          value={position.moneyValues[valueIn].totalGain}
          displayType={"text"}
          decimalScale={2}
          fixedDecimalScale={true}
          thousandSeparator={true}
        />
      </td>
    </tr>
  ));
  return <tbody>{holdings}</tbody>;
}

export function HoldingFooter(
  holdingGroup: HoldingGroup,
  valueIn: ValuationCcy
): JSX.Element {
  return (
    <tbody className={"holding-totals-row"}>
      <tr key={holdingGroup.group} className={"holding-footer"}>
        <td colSpan={4} align={"right"}>
          Sub-Total - {holdingGroup.subTotals[valueIn].currency.code}
        </td>
        <td align={"right"}>
          <NumberFormat
            value={holdingGroup.subTotals[valueIn].costValue}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </td>

        <td align={"right"}>
          <NumberFormat
            value={holdingGroup.subTotals[valueIn].marketValue}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </td>

        <td align={"right"}>
          <NumberFormat
            value={holdingGroup.subTotals[valueIn].dividends}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </td>

        <td align={"right"}>
          <NumberFormat
            value={holdingGroup.subTotals[valueIn].realisedGain}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </td>

        <td align={"right"}>
          <NumberFormat
            value={holdingGroup.subTotals[valueIn].unrealisedGain}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </td>

        <td align={"right"}>
          <NumberFormat
            value={holdingGroup.subTotals[valueIn].totalGain}
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
