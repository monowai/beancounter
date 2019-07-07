import {HoldingContract, Holdings, MoneyValues, Position} from "../types/beancounter";

export const LOCAL = "LOCAL";
// Transform the holdingContract into Holdings suitable for display
export function groupHoldings(
  contract: HoldingContract,
  hideEmpty: boolean,
  groupBy: string
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
          position
        );

        return results;
      },
      { portfolio: contract.portfolio, holdingGroups: [] }
    );
}

function subTotal(subTotals: MoneyValues[], position: Position): MoneyValues[] {
  if (!subTotals) {
    subTotals = [];
    subTotals[LOCAL] = {
      costValue: 0,
      dividends: 0,
      marketValue: 0,
      realisedGain: 0,
      totalGain: 0,
      unrealisedGain: 0
    };
  }
  subTotals[LOCAL].marketValue =
    subTotals[LOCAL].marketValue + position.moneyValues[LOCAL].marketValue;
  subTotals[LOCAL].costValue =
    subTotals[LOCAL].costValue + position.moneyValues[LOCAL].costValue;
  subTotals[LOCAL].dividends =
    subTotals[LOCAL].dividends + position.moneyValues[LOCAL].dividends;
  subTotals[LOCAL].realisedGain =
    subTotals[LOCAL].realisedGain + position.moneyValues[LOCAL].realisedGain;
  subTotals[LOCAL].unrealisedGain =
    subTotals[LOCAL].unrealisedGain +
    position.moneyValues[LOCAL].unrealisedGain;
  subTotals[LOCAL].totalGain =
    subTotals[LOCAL].totalGain + position.moneyValues[LOCAL].totalGain;

  return subTotals;
}

function getPath(path: string, position: Position): string {
  return (path
    .split(".")
    .reduce(
      (p, c) => (p && p[c]) || "undefined",
      position
    ) as unknown) as string;
}
