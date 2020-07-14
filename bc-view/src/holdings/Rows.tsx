import { HoldingGroup, Portfolio } from "../types/beancounter";
import { ValueIn } from "../types/valueBy";
import NumberFormat from "react-number-format";
import { Link } from "react-router-dom";
import { FormatNumber } from "../common/MoneyUtils";
import React from "react";

export function Rows(props: {
  portfolio: Portfolio;
  holdingGroup: HoldingGroup;
  valueIn: ValueIn;
}): JSX.Element {
  const valueIn = props.valueIn;
  // eslint-disable-next-line complexity
  const holdings = props.holdingGroup.positions.map((position, index) => (
    <tr key={props.holdingGroup.group + index} className={"holding-row"}>
      <td className={"asset"}>{position.asset.code + ": " + position.asset.name}</td>
      <td className={"price"} align={"right"}>
        {
          <span
            data-tooltip={
              position.moneyValues[valueIn].priceData
                ? position.moneyValues[valueIn].priceData.priceDate
                : ""
            }
          >
            {position.moneyValues[valueIn].currency.id}
            {position.moneyValues[valueIn].currency.symbol}
            <FormatNumber values={position.moneyValues[valueIn].priceData} field={"close"} />
          </span>
        }
      </td>
      <td align={"right"}>
        {!position.moneyValues[valueIn].priceData ? (
          "-"
        ) : (
          <span
            className={
              position.moneyValues[valueIn].priceData.changePercent < 0
                ? "negative-gain"
                : "positive-gain"
            }
            data-tooltip={
              "Previous " +
              position.moneyValues[valueIn].currency.symbol +
              " " +
              position.moneyValues[valueIn].priceData.previousClose
            }
          >
            {(position.moneyValues[valueIn].priceData.changePercent * 100).toFixed(2)}%
          </span>
        )}
      </td>
      <td align={"right"}>
        <Link to={`/trns/${props.portfolio.id}/asset/${position.asset.id}/trades`}>
          <NumberFormat
            value={position.quantityValues.total}
            displayType={"text"}
            decimalScale={position.quantityValues.precision}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </Link>
      </td>
      <td align={"right"}>
        <FormatNumber values={position.moneyValues[valueIn]} field={"marketValue"} />
      </td>
      <td align={"right"}>
        <FormatNumber values={position.moneyValues[valueIn]} field={"unrealisedGain"} />
      </td>
      <td align={"right"}>
        <FormatNumber values={position.moneyValues[valueIn]} field={"weight"} multiplier={100} />%
      </td>
      <td align={"right"}>
        <FormatNumber values={position.moneyValues[valueIn]} field={"costValue"} />
      </td>
      <td align={"right"}>
        <FormatNumber values={position.moneyValues[valueIn]} field={"averageCost"} />
      </td>
      <td align={"right"}>
        {
          <span
            data-tooltip={
              position.dateValues ? "Last Event: " + position.dateValues.lastDividend : "N/A"
            }
          >
            <Link to={`/trns/${props.portfolio.id}/asset/${position.asset.id}/events`}>
              <FormatNumber values={position.moneyValues[valueIn]} field={"dividends"} />
            </Link>
          </span>
        }
      </td>
      <td align={"right"}>
        <FormatNumber values={position.moneyValues[valueIn]} field={"realisedGain"} />
      </td>
      <td align={"right"}>
        <FormatNumber values={position.moneyValues[valueIn]} field={"totalGain"} />
      </td>
    </tr>
  ));
  return <tbody>{holdings}</tbody>;
}
