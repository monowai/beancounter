// export type ValueIn = "TRADE" | "BASE" | "PORTFOLIO";

import { ValuationOption } from "./beancounter";
import { translate } from "../common/i18nUtils";

// Enum is pointer to a collection of values in the holding contract
export enum ValueIn {
  TRADE = "TRADE",
  BASE = "BASE",
  PORTFOLIO = "PORTFOLIO",
}

export function valuationOptions(): ValuationOption[] {
  return [
    { value: ValueIn.PORTFOLIO, label: translate("valuein.portfolio") },
    { value: ValueIn.BASE, label: translate("valuein.base") },
    { value: ValueIn.TRADE, label: translate("valuein.trade") },
  ];
}
