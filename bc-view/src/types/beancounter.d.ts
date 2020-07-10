import { GroupBy } from "./groupBy";
import { ValuationCcy } from "./valueBy";

export type TrnType = "BUY" | "SELL" | "DIVI" | "SPLIT";

export interface Market {
  code: string;
  currency: Currency;
}

export interface Currency {
  code: string;
  symbol: string;
}

export interface Asset {
  id: string;
  code: string;
  name: string;
  market: Market;
}

export interface MoneyValues {
  dividends: number;
  costValue: number;
  fees: number;
  tax: number;
  purchases: number;
  sales: number;
  costBasis: number;
  averageCost: number;
  realisedGain: number;
  unrealisedGain: number;
  totalGain: number;
  priceData: PriceData;
  marketValue: number;
  currency: Currency;
  valueIn: ValuationCcy;
}

export interface PriceData {
  change: number;
  close: number;
  previousClose: number;
  changePercent: number;
  priceDate: string;
}

export interface QuantityValues {
  sold: number;
  purchased: number;
  total: number;
  precision: number;
}

export interface Position {
  asset: Asset;
  moneyValues: MoneyValues[];
  quantityValues: QuantityValues;
  dateValues: DateValues;
  lastTradeDate: string;
}
export interface Interface {
  lastDividend: string;
}

export interface Portfolio {
  id: string;
  code: string;
  name: string;
  currency: Currency;
  base: Currency;
  owner?: SystemUser;
}

export interface PortfolioInput {
  code: string;
  name: string;
  currency: string;
  base: string;
}

// Server side contract
interface HoldingContract {
  portfolio: Portfolio;
  asAt: string;
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

interface ValuationOption {
  label: string;
  value: ValuationCcy;
}

interface SystemUser {
  active: boolean;
  email: string | undefined;
}

interface TrnInput {
  trnType: TrnType;
  portfolioId: string;
  assetId: string;
  tradeDate: string;
  quantity: number;
  price: number;
  tradeAmount: number;
  fees: number;
}

interface CallerRef {
  provider: string;
  batch: string;
  callerId: string;
}

interface Transaction {
  id: string;
  callerRef: CallerRef;
  trnType: TrnType;
  portfolio: Portfolio;
  asset: Asset;
  tradeDate: string;
  quantity: number;
  price: number;
  fees: number;
  tradeAmount: number;
  tradeCurrency: Currency;
  tradeCashRate: number;
  tradeBaseRate: number;
  tradePortfolioRate: number;
  comments: string;
}
