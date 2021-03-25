import { GroupOption } from "./beancounter";
import { translate } from "../common/i18nUtils";

// Enum is a path to a property in the holding contract
export enum GroupBy {
  MARKET_CURRENCY = "asset.market.currency.code",
  MARKET = "asset.market.code",
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

export function groupOptions(): GroupOption[] {
  return [
    {
      value: GroupBy.MARKET_CURRENCY,
      label: description(GroupBy.MARKET_CURRENCY),
    },
    { value: GroupBy.MARKET, label: description(GroupBy.MARKET) },
  ];
}
