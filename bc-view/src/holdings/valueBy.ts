// export type CurrencyValues = "TRADE" | "BASE" | "PORTFOLIO";

export enum CurrencyValues {
  TRADE = "TRADE",
  BASE = "BASE",
  PORTFOLIO = "PORTFOLIO"
}

// fixme - label: translate(CurrencyValues.MARKET_CURRENCY)  is "undefined" at this point
export const CurrencyOptions = [
  { value: CurrencyValues.PORTFOLIO, label: "Portfolio" },
  { value: CurrencyValues.BASE, label: "Base" },
  { value: CurrencyValues.TRADE, label: "Trade" }
];
