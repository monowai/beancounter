import NumberFormat from "react-number-format";
import React from "react";
import { MoneyFields, MoneyValues } from "../types/beancounter";

export function FormatMoneyValue(props: {
  moneyValues: MoneyValues;
  moneyField: MoneyFields;
}): JSX.Element {
  if (props.moneyValues && props.moneyValues[props.moneyField]) {
    return (
      <NumberFormat
        value={props.moneyValues[props.moneyField]}
        displayType={"text"}
        decimalScale={2}
        fixedDecimalScale={true}
        thousandSeparator={true}
      />
    );
  }
  return <div>-</div>;
}
