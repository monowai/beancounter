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

function total(
  totals: MoneyValues[],
  position: Position,
  valueIn: ValuationCcy
): MoneyValues[] {
  if (!totals) {
    totals = [];
    totals[valueIn] = {
      costValue: 0,
      dividends: 0,
      marketValue: 0,
      realisedGain: 0,
      totalGain: 0,
      unrealisedGain: 0,
      currency: position.moneyValues[valueIn].currency
    };
  }
  totals[valueIn].marketValue += position.moneyValues[valueIn].marketValue;
  totals[valueIn].costValue =
    totals[valueIn].costValue + position.moneyValues[valueIn].costValue;
  totals[valueIn].dividends =
    totals[valueIn].dividends + position.moneyValues[valueIn].dividends;
  totals[valueIn].realisedGain =
    totals[valueIn].realisedGain + position.moneyValues[valueIn].realisedGain;
  totals[valueIn].unrealisedGain =
    totals[valueIn].unrealisedGain +
    position.moneyValues[valueIn].unrealisedGain;
  totals[valueIn].totalGain =
    totals[valueIn].totalGain + position.moneyValues[valueIn].totalGain;

  return totals;
}

// Transform the holdingContract into Holdings suitable for display
export function prepHoldings(
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
        results.holdingGroups[groupKey].subTotals = total(
          results.holdingGroups[groupKey].subTotals,
          position,
          valueIn
        );
        results.valueIn = valueIn;
        return results;
      },
      { portfolio: contract.portfolio, holdingGroups: [], valueIn: "PORTFOLIO" }
    );
}
