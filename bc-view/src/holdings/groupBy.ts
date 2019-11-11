import { translate } from "../i18nConfig";

export type ValuationCcy = "TRADE" | "BASE" | "PORTFOLIO";

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
