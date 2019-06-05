import { HoldingContract, Holdings, Position } from "../types/beancounter";

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
        results.holdingGroups[groupKey].total =
          results.holdingGroups[groupKey].total + 100;

        // results.holdingGroups[groupKey].push(position);

        return results;
      },
      { portfolio: contract.portfolio, holdingGroups: [] }
    );
}

function getPath(path: string, position: Position): string {
  return (path
    .split(".")
    .reduce(
      (p, c) => (p && p[c]) || "undefined",
      position
    ) as unknown) as string;
}
