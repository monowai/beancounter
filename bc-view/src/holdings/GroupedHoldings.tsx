import React from "react";
import NumberFormat from "react-number-format";
import { useTranslation } from "react-i18next";
import { HoldingGroup, Holdings } from "../types/beancounter";
import { ValuationCcy } from "./enums";
import i18n from "../ConfigI18n";

function writeHoldings(
  holdingGroup: HoldingGroup,
  valueIn: ValuationCcy
): JSX.Element {
  // eslint-disable-next-line complexity
  const rows = holdingGroup.positions.map((position, index) => (
    <tr key={holdingGroup.group + index}>
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
            {position.moneyValues[valueIn].currency.code}
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
  return <tbody>{rows}</tbody>;
}

export function writeFooter(
  holdingGroup: HoldingGroup,
  valueIn: ValuationCcy
): JSX.Element {
  return (
    <tfoot>
      <tr key={holdingGroup.group}>
        <th colSpan={4} align={"right"}>
          Sub-Total - {holdingGroup.subTotals[valueIn].currency.code}
        </th>
        <th align={"right"}>
          <NumberFormat
            value={holdingGroup.subTotals[valueIn].costValue}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </th>

        <th align={"right"}>
          <NumberFormat
            value={holdingGroup.subTotals[valueIn].marketValue}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </th>

        <th align={"right"}>
          <NumberFormat
            value={holdingGroup.subTotals[valueIn].dividends}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </th>

        <th align={"right"}>
          <NumberFormat
            value={holdingGroup.subTotals[valueIn].realisedGain}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </th>

        <th align={"right"}>
          <NumberFormat
            value={holdingGroup.subTotals[valueIn].unrealisedGain}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </th>

        <th align={"right"}>
          <NumberFormat
            value={holdingGroup.subTotals[valueIn].totalGain}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </th>
      </tr>
    </tfoot>
  );
}

export default function GroupedHoldings(holdings: Holdings): JSX.Element {
  const { t } = useTranslation("translations", { i18n });
  // Transform the contract into the view the user requested
  const rows = Object.keys(holdings.holdingGroups).map((groupKey, index) => {
    return (
      <div className={"table-container is-large"} key={index}>
        <br />
        <table className={"table is-striped is-hoverable"}>
          <thead>
            <tr>
              <th>{groupKey}</th>
              <th align={"right"}>{t("quantity")}</th>
              <th align={"right"}>{t("price")}</th>
              <th align={"right"}>{t("cost.avg")}</th>
              <th align={"right"}>{t("cost")}</th>
              <th align={"right"}>{t("value")}</th>
              <th align={"right"}>{t("dividends")}</th>
              <th align={"right"}>{t("gain.realised")}</th>
              <th align={"right"}>{t("gain.unrealised")}</th>
              <th align={"right"}>{t("gain")}</th>
            </tr>
          </thead>
          {writeHoldings(holdings.holdingGroups[groupKey], holdings.valueIn)}
          {writeFooter(holdings.holdingGroups[groupKey], holdings.valueIn)}
        </table>
      </div>
    );
  });
  return <React.Fragment>{rows}</React.Fragment>;
}
