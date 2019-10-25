import React from "react";
import NumberFormat from "react-number-format";
import { groupHoldings } from "./groupHoldings";
import { HoldingContract, HoldingGroup, Holdings } from "../types/beancounter";
import { GroupBy, ValuationCcy } from "./enums";

export function writeHeader(groupKey: string): JSX.Element {
  return (
    <table>
      <thead>
      <tbody>
      <tr>
        <th>{groupKey}</th>
        <th>Quantity</th>
        <th>Price</th>
        <th>Avg Cost</th>
        <th>Cost</th>
        <th>Value</th>
        <th>Dividends</th>
        <th>Realised</th>
        <th>Unrealised</th>
        <th>Gain</th>
      </tr>
      </tbody>

      </thead>
    </table>
  );
}

function writeHoldings(
  holdingGroup: HoldingGroup,
  valueIn: ValuationCcy
): any[] {
  console.log("Tweee");
  // eslint-disable-next-line complexity
  return holdingGroup.positions.map(position => (
    <tr key={holdingGroup.group + position.asset.code}>
      <td>{position.asset.code + ": " + position.asset.name}</td>
      <td>
        {
          <NumberFormat
            value={position.quantityValues.total}
            displayType={"text"}
            decimalScale={0}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        }
      </td>
      <td>
        {!position.asset.market.currency ? (
          "-"
        ) : (
          <label>
            {position.asset.market.currency.id}{" "}
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
}

export function writeFooter(
  holdingGroup: HoldingGroup,
  valueIn: ValuationCcy
): JSX.Element {
  return (
    <tfoot>
    <tbody>

    </tbody>
    <tr>
      <th/>
      <th>Sub-Total</th>
      <th/>
      <td>
        <NumberFormat
          value={holdingGroup.subTotals[valueIn].costValue}
          displayType={"text"}
          decimalScale={2}
          fixedDecimalScale={true}
          thousandSeparator={true}
        />
      </td>

      <td>
        <NumberFormat
          value={holdingGroup.subTotals[valueIn].marketValue}
          displayType={"text"}
          decimalScale={2}
          fixedDecimalScale={true}
          thousandSeparator={true}
        />
      </td>

      <td>
        <NumberFormat
          value={holdingGroup.subTotals[valueIn].dividends}
          displayType={"text"}
          decimalScale={2}
          fixedDecimalScale={true}
          thousandSeparator={true}
        />
      </td>

      <td>
        <NumberFormat
          value={holdingGroup.subTotals[valueIn].realisedGain}
          displayType={"text"}
          decimalScale={2}
          fixedDecimalScale={true}
          thousandSeparator={true}
        />
      </td>

      <td>
        <NumberFormat
          value={holdingGroup.subTotals[valueIn].unrealisedGain}
          displayType={"text"}
          decimalScale={2}
          fixedDecimalScale={true}
          thousandSeparator={true}
        />
      </td>

      <td>
        <NumberFormat
          value={holdingGroup.subTotals[valueIn].totalGain}
          displayType={"text"}
          decimalScale={2}
          fixedDecimalScale={true}
          thousandSeparator={true}
        />
      </td>
    </tr>
    </tfoot>
  );
}

export default function GroupedHoldings(contract: HoldingContract): JSX.Element {
  const valueIn = ValuationCcy.PORTFOLIO;
  // Transform the contract into the view the user requested
  const holdings = groupHoldings(
    contract,
    true,
    valueIn,
    GroupBy.MARKET_CURRENCY
  ) as Holdings;

  const rows = Object.keys(holdings.holdingGroups).map(groupKey => {
    // Build out the header
    return (
      <React.Fragment key={groupKey}>
        <table>
          {writeHeader(groupKey)}
          {writeHoldings(
            holdings.holdingGroups[groupKey],
            valueIn
          )}
          {writeFooter(
            holdings.holdingGroups[groupKey],
            valueIn
          )}
        </table>
      </React.Fragment>
    );
  });
  return <table>{rows}</table>;
}
