import { HoldingGroup, Portfolio } from "../types/beancounter";
import { ValueIn } from "../types/valueBy";
import NumberFormat from "react-number-format";
import { Link } from "react-router-dom";
import { FormatMoneyValue } from "../common/MoneyUtils";
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
      <td align={"right"}>
        {!position.asset.market.currency ? (
          "-"
        ) : (
          <label>
            {position.moneyValues[valueIn].currency.id}
            {position.moneyValues[valueIn].currency.symbol}
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
      <td align={"right"}>
        <Link to={`/trns/${props.portfolio.id}/${position.asset.id}`}>
          <NumberFormat
            value={position.quantityValues.total}
            displayType={"text"}
            decimalScale={0}
            fixedDecimalScale={true}
            thousandSeparator={true}
          />
        </Link>
      </td>
      <td align={"right"}>
        <FormatMoneyValue moneyValues={position.moneyValues[valueIn]} moneyField={"marketValue"}/>
      </td>
      <td align={"right"}>
        <FormatMoneyValue moneyValues={position.moneyValues[valueIn]} moneyField={"costValue"}/>
      </td>
      <td align={"right"}>
        <FormatMoneyValue moneyValues={position.moneyValues[valueIn]} moneyField={"averageCost"}/>
      </td>
      <td align={"right"}>
        <FormatMoneyValue moneyValues={position.moneyValues[valueIn]} moneyField={"dividends"}/>
      </td>
      <td align={"right"}>
        <FormatMoneyValue moneyValues={position.moneyValues[valueIn]} moneyField={"realisedGain"}/>
      </td>
      <td align={"right"}>
        <FormatMoneyValue
          moneyValues={position.moneyValues[valueIn]}
          moneyField={"unrealisedGain"}
        />
      </td>
      <td align={"right"}>
        <FormatMoneyValue moneyValues={position.moneyValues[valueIn]} moneyField={"totalGain"}/>
      </td>
    </tr>
  ));
  return <tbody>{holdings}</tbody>;
}