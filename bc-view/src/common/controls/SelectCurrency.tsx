import React from "react";
import { useForm } from "react-hook-form";

export function SelectCurrency(props: { default: string; name: string }): React.ReactElement {
  const { register, handleSubmit, errors } = useForm();
  return (
    <select
      placeholder={"SelectCurrency"}
      className={"select is-3"}
      defaultValue={props.default}
      name={props.name}
      ref={register({ required: true })}
    >
      <option value="USD">USD</option>
      <option value="EUR">EUR</option>
      <option value="SGD">SGD</option>
      <option value="NZD">NZD</option>
    </select>
  );
}
