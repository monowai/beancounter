import { HoldingContract, Holdings, MoneyValues, Position } from "../types/beancounter";
import { GroupBy, ValuationCcy } from "./enums";

function getPath(path: string, position: Position): string {
  return (path
    .split(".")
    .reduce(
      (p, c) => (p && p[c]) || "undefined",
      position
    ) as unknown) as string;
}

function subTotal(subTotals: MoneyValues[], position: Position, valueIn: ValuationCcy): MoneyValues[] {
  if (!subTotals) {
    subTotals = [];
    subTotals[valueIn] = {
      costValue: 0,
      dividends: 0,
      marketValue: 0,
      realisedGain: 0,
      totalGain: 0,
      unrealisedGain: 0
    };
  }
  subTotals[valueIn].marketValue +=
    position.moneyValues[valueIn].marketValue;
  subTotals[valueIn].costValue =
    subTotals[valueIn].costValue +
    position.moneyValues[valueIn].costValue;
  subTotals[valueIn].dividends =
    subTotals[valueIn].dividends +
    position.moneyValues[valueIn].dividends;
  subTotals[valueIn].realisedGain =
    subTotals[valueIn].realisedGain +
    position.moneyValues[valueIn].realisedGain;
  subTotals[valueIn].unrealisedGain =
    subTotals[valueIn].unrealisedGain +
    position.moneyValues[valueIn].unrealisedGain;
  subTotals[valueIn].totalGain =
    subTotals[valueIn].totalGain +
    position.moneyValues[valueIn].totalGain;

  return subTotals;
}

// Transform the holdingContract into Holdings suitable for display
export function groupHoldings(
  contract: HoldingContract,
  hideEmpty: boolean,
  valueIn: ValuationCcy,
  groupBy: GroupBy
): Holdings {
  return Object.keys(contract.positions)
    .filter(positionKey =>
      hideEmpty
        ? contract.positions[positionKey].quantityValues.total !== 0
        : true
    )
    .reduce(
      (results: Holdings, group) => {
        const position = contract.positions[group] as Position;
        const groupKey = getPath(groupBy, position);

        results.holdingGroups[groupKey] = results.holdingGroups[groupKey] || {
          group: groupKey,
          positions: [],
          total: 0
        };
        results.holdingGroups[groupKey].positions.push(position);
        results.holdingGroups[groupKey].subTotals = subTotal(
          results.holdingGroups[groupKey].subTotals,
          position, valueIn
        );

        return results;
      },
      { portfolio: contract.portfolio, holdingGroups: [] }
    );
}
