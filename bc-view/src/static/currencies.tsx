import { Currency } from "../types/beancounter";
import React, { ReactNode } from "react";

export function byCurrencyCode(code: string, currencies: Currency[]): Currency | undefined {
  return currencies.find(element => element.code === code);
}

export function currencyOptions(currencies: Currency[]): ReactNode {
  return currencies.map((currency: Currency) => (
    <option key={currency.code} value={currency.code}>
      {currency.code}
    </option>
  ));
}
