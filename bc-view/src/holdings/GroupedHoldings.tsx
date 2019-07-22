import React from "react";
import {HoldingContract, HoldingGroup, Holdings} from "../types/beancounter";
import {Table} from "semantic-ui-react";
import NumberFormat from "react-number-format";
import {groupHoldings, LOCAL} from "./groupHoldings";

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
          {writeHeader(groupKey)}
          {writeHoldings(holdings.holdingGroups[groupKey])}
          {writeFooter(holdings.holdingGroups[groupKey])}
        </Table>
      </React.Fragment>
    );
  });
  return <Table.Body>{rows}</Table.Body>;
}

export function writeHeader(groupKey: string) {
  return (
    <Table.Header>
      <Table.Row textAlign="right">
        <Table.HeaderCell textAlign="left" colSpan="2">
          {groupKey}
        </Table.HeaderCell>
        <Table.HeaderCell>Quantity</Table.HeaderCell>
        <Table.HeaderCell>Price</Table.HeaderCell>
        <Table.HeaderCell>Avg Cost</Table.HeaderCell>
        <Table.HeaderCell>Cost Value</Table.HeaderCell>
        <Table.HeaderCell>Market Value</Table.HeaderCell>
        <Table.HeaderCell>Dividends</Table.HeaderCell>
        <Table.HeaderCell>Realised Gain</Table.HeaderCell>
        <Table.HeaderCell>Unrealised Gain</Table.HeaderCell>
        <Table.HeaderCell>Total Gain</Table.HeaderCell>
      </Table.Row>
    </Table.Header>
  );
}

function writeHoldings(holdingGroup: HoldingGroup) {
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
        {!position.asset.market.currency ? (
          "-"
        ) : (
          <label>
            {position.asset.market.currency.id}{" "}
            <NumberFormat
              value={position.moneyValues[LOCAL].price}
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
            value={position.moneyValues[LOCAL].averageCost}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        )}
      </Table.Cell>
      <Table.Cell>
        {!position.moneyValues ? (
          "-"
        ) : (
          <NumberFormat
            value={position.moneyValues[LOCAL].costValue}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        )}
      </Table.Cell>

      <Table.Cell>
        {!position.moneyValues[LOCAL] ? (
          "-"
        ) : (
          <NumberFormat
            value={position.moneyValues[LOCAL].marketValue}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        )}
      </Table.Cell>
      <Table.Cell>
        <NumberFormat
          value={position.moneyValues[LOCAL].dividends}
          displayType={"text"}
          decimalScale={2}
          fixedDecimalScale={true}
          thousandSeparator={true}
        />
      </Table.Cell>
      <Table.Cell>
        <NumberFormat
          value={position.moneyValues[LOCAL].realisedGain}
          displayType={"text"}
          decimalScale={2}
          fixedDecimalScale={true}
          thousandSeparator={true}
        />
      </Table.Cell>
      <Table.Cell>
        {!position.moneyValues[LOCAL] ? (
          "-"
        ) : (
          <NumberFormat
            value={position.moneyValues[LOCAL].unrealisedGain}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        )}
      </Table.Cell>
      <Table.Cell>
        <NumberFormat
          value={position.moneyValues[LOCAL].totalGain}
          displayType={"text"}
          decimalScale={2}
          fixedDecimalScale={true}
          thousandSeparator={true}
        />
      </Table.Cell>
    </Table.Row>
  ));
}

export function writeFooter(holdingGroup: HoldingGroup) {
  return (
    <Table.Footer>
      <Table.Row textAlign="right" className={"warning"}>
        <Table.Cell textAlign="left" colSpan="2"/>
        <Table.Cell textAlign="right">Sub-Total</Table.Cell>
        <Table.Cell textAlign="left" colSpan="2"/>
        {/*<Table.Cell textAlign="left" colSpan="4"/>*/}
        <Table.Cell>
          <NumberFormat
            value={holdingGroup.subTotals[LOCAL].costValue}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </Table.Cell>

        <Table.Cell>
          <NumberFormat
            value={holdingGroup.subTotals[LOCAL].marketValue}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </Table.Cell>

        <Table.Cell>
          <NumberFormat
            value={holdingGroup.subTotals[LOCAL].dividends}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </Table.Cell>

        <Table.Cell>
          <NumberFormat
            value={holdingGroup.subTotals[LOCAL].realisedGain}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </Table.Cell>

        <Table.Cell>
          <NumberFormat
            value={holdingGroup.subTotals[LOCAL].unrealisedGain}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </Table.Cell>

        <Table.Cell>
          <NumberFormat
            value={holdingGroup.subTotals[LOCAL].totalGain}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </Table.Cell>
      </Table.Row>
    </Table.Footer>
  );
}
