import React, { ReactNode } from "react";
import { Currency } from "../types/beancounter";

export function currencyOptions(currencies: Currency[], selectedValue: string): ReactNode {
  return currencies.map((currency: Currency) => (
    <option key={currency.code} value={currency.code} selected={currency.code === selectedValue}>
      {currency.code}
    </option>
  ));
}
