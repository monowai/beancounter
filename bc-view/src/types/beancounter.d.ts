import { GroupBy } from "../holdings/groupBy";
import { ValuationCcy } from "../holdings/valueBy";

declare global {
  interface Window {
    initialI18nStore: any;
    initialLanguage: any;
  }
}

export type MoneyFields =
  | "dividends"
  | "price"
  | "costValue"
  | "averageCost"
  | "marketValue"
  | "realisedGain"
  | "unrealisedGain"
  | "totalGain"
  | "sales"
  | "purchases"
  | "purchases"
  | "marketValue"
  | "investmentGain";

export interface Market {
  code: string;
  currency: Currency;
}

export interface Currency {
  id: string;
  code: string;
  symbol: string;
}

interface Asset {
  code: string;
  name: string;
  market: Market;
}

interface MoneyValues {
  dividends: number;
  costValue: number;
  fees: number;
  purchases: number;
  sales: number;
  costBasis: number;
  averageCost: number;
  realisedGain: number;
  unrealisedGain: number;
  totalGain: number;
  price: number;
  marketValue: number;
  currency: Currency;
  valueIn: ValuationCcy;
}

interface QuantityValues {
  sold: number;
  purchased: number;
  total: number;
}

export interface Position {
  asset: Asset;
  moneyValues: MoneyValues[];
  quantityValues: QuantityValues;
  lastTradeDate: string;
}

interface Portfolio {
  code: string;
  currency: Currency;
  base: Currency;
}
// Server side contract
interface HoldingContract {
  portfolio: Portfolio;
  positions: Position[];
}

// The payload we render in the UI
interface Holdings {
  holdingGroups: HoldingGroup[];
  portfolio: Portfolio;
  valueIn: ValuationCcy;
  totals: MoneyValues[];
}

// User defined grouping
interface HoldingGroup {
  group: string;
  positions: Position[];
  subTotals: MoneyValues[];
}

interface GroupOption {
  label: string;
  value: GroupBy;
}

interface CurrencyOption {
  label: string;
  value: ValuationCcy;
}
