import NumberFormat from "react-number-format";
import React from "react";

export function FormatNumber(props: {
  values: [];
  field: string;
  scale?: number;
  multiplier?: number;
}): JSX.Element {
  if (props.values && props.values[props.field]) {
    return (
      <NumberFormat
        value={props.values[props.field] * (props.multiplier ? props.multiplier : 1)}
        displayType={"text"}
        decimalScale={props.scale ? props.scale : 2}
        fixedDecimalScale={true}
        thousandSeparator={true}
      />
    );
  }
  return <span>-</span>;
}
