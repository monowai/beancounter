import { translate } from "../i18nConfig";

export enum GroupBy {
  MARKET_CURRENCY = "asset.market.currency.code",
  MARKET = "asset.market.code"
}

export function description(groupBy: GroupBy): string {
  if (groupBy === GroupBy.MARKET_CURRENCY) {
    return translate("groupby.currency");
  }
  if (groupBy === GroupBy.MARKET) {
    return translate("groupby.market");
  }
  return translate("groupby.unknown");
}

// fixme - label: translate(CurrencyValues.MARKET_CURRENCY)  is "undefined" at this point
export const GroupOptions = [
  { value: GroupBy.MARKET_CURRENCY, label: "Currency" },
  { value: GroupBy.MARKET, label: "Market" }
];
