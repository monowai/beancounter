import { ValuationCcy } from "../holdings/enums";

declare global {
  interface Window {
    initialI18nStore: any;
    initialLanguage: any;
  }
}

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
  moneyValues: { [ValuationCCy: ValuationCcy]: MoneyValues };
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
  totals: { [ValuationCCy: ValuationCcy]: MoneyValues };
}

// User defined grouping
interface HoldingGroup {
  group: string;
  positions: Position[];
  subTotals: { [ValuationCCy: ValuationCcy]: MoneyValues };
}
