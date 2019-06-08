import React from "react";
import {HoldingContract, HoldingGroup, Holdings} from "../types/beancounter";
import {Table} from "semantic-ui-react";
import NumberFormat from "react-number-format";
import {groupHoldings} from "./groupHoldings";

export default function GroupedHoldings(contract: HoldingContract) {
  // Transform the contract into the view the user requested
  const holdings = groupHoldings(
    contract,
    true,
    "asset.market.currency.code"
  ) as Holdings;

  const rows = Object.keys(holdings.holdingGroups).map(groupKey => {
    // Build out the header
    return (
      <React.Fragment key={groupKey}>
        <Table striped={true} fixed={false} compact={"very"}>
          {HoldingHeader(groupKey)}
          {WriteHoldings(holdings.holdingGroups[groupKey])}
        </Table>
      </React.Fragment>
    );
  });
  return <Table.Body>{rows}</Table.Body>;
}

export function HoldingHeader(groupKey: string) {
  return (
    <Table.Header>
      <Table.Row textAlign="right">
        <Table.HeaderCell textAlign="left" colSpan="2">
          {groupKey}
        </Table.HeaderCell>
        <Table.HeaderCell>Quantity</Table.HeaderCell>
        <Table.HeaderCell>Price</Table.HeaderCell>
        <Table.HeaderCell>Avg Cost</Table.HeaderCell>
        <Table.HeaderCell>Market Value</Table.HeaderCell>
        <Table.HeaderCell>Dividends</Table.HeaderCell>
        <Table.HeaderCell>Realised Gain</Table.HeaderCell>
      </Table.Row>
    </Table.Header>
  );
}

function WriteHoldings(holdingGroup: HoldingGroup) {
  return holdingGroup.positions.map(position => (
    <Table.Row textAlign="right" key={holdingGroup.group + position.asset.code}>
      <Table.Cell textAlign="left" colSpan="2">
        {position.asset.code + ": " + position.asset.name}
      </Table.Cell>
      <Table.Cell>
        {
          <NumberFormat
            value={position.quantityValues.total}
            displayType={"text"}
            decimalScale={0}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        }
      </Table.Cell>
      <Table.Cell>
        {!position.marketValue ? (
          "-"
        ) : (
          <label>
            {position.asset.market.currency.id}{position.asset.market.currency.symbol}
            {" "}
            <NumberFormat
              value={position.marketValue.price}
              displayType={"text"}
              decimalScale={2}
              fixedDecimalScale={true}
              thousandSeparator={true}
            />
          </label>
        )}
      </Table.Cell>

      <Table.Cell>
        {!position.moneyValues ? (
          "-"
        ) : (
          <NumberFormat
            value={position.moneyValues.averageCost}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        )}
      </Table.Cell>
      <Table.Cell>
        {!position.marketValue ? (
          "-"
        ) : (
          <NumberFormat
            value={position.marketValue.marketValue}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        )}
      </Table.Cell>
      <Table.Cell>
        <NumberFormat
          value={position.moneyValues.dividends}
          displayType={"text"}
          decimalScale={2}
          fixedDecimalScale={true}
          thousandSeparator={true}
        />
      </Table.Cell>
      <Table.Cell>
        <NumberFormat
          value={position.moneyValues.realisedGain}
          displayType={"text"}
          decimalScale={2}
          fixedDecimalScale={true}
          thousandSeparator={true}
        />
      </Table.Cell>
    </Table.Row>
  ));
}
