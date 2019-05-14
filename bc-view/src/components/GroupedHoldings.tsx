import React from "react";
import { Holdings, HoldingContract, HoldingGroup } from "../types/beancounter";
import { Table } from "semantic-ui-react";
import NumberFormat from "react-number-format";
import { groupHoldings } from "../helpers/groupHoldings";

class GroupedHoldings extends React.Component<HoldingContract> {
  render() {
    // Transform the contract into the way the user wants to see it presented
    const holdings = groupHoldings(
      this.props,
      true,
      "asset.market.code"
    ) as Holdings;

    const rows = Object.keys(holdings.holdingGroups).map(groupKey => {
      // Build out the header
      return (
        <React.Fragment key={groupKey}>
          <Table striped={true} fixed={false} compact={"very"}>
            {this.holdingHeader(groupKey)}
            {this.groupedHoldings(holdings.holdingGroups[groupKey])}
          </Table>
        </React.Fragment>
      );
    });
    return <Table.Body>{rows}</Table.Body>;
  }

  holdingHeader(groupKey: string) {
    const header = (
      <Table.Header>
        <Table.Row textAlign="right">
          <Table.HeaderCell textAlign="left" colSpan="2">
            {groupKey}: Asset
          </Table.HeaderCell>
          <Table.HeaderCell>Quantity</Table.HeaderCell>
          <Table.HeaderCell>Avg Cost</Table.HeaderCell>
          <Table.HeaderCell>Market Value</Table.HeaderCell>
          <Table.HeaderCell>Dividends</Table.HeaderCell>
          <Table.HeaderCell>Realised Gain</Table.HeaderCell>
        </Table.Row>
      </Table.Header>
    );
    return header;
  }

  groupedHoldings(holdingGroup: HoldingGroup) {
    const groupedHoldings = holdingGroup.positions.map(position => (
      <Table.Row
        textAlign="right"
        key={holdingGroup.group + position.asset.code}
      >
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
            value={position.moneyValues.realisedGain}
            displayType={"text"}
            decimalScale={2}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
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
      </Table.Row>
    ));
    return groupedHoldings;
  }
}

export default GroupedHoldings;
