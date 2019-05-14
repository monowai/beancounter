export interface Market {
  code: string;
}

interface Asset {
  code: string;
  name: string;
  market: Market;
}

interface MarketValue {
  price: number;
  marketValue: number;
}

interface MoneyValues {
  dividends: number;
  marketCost: number;
  fees: number;
  purchases: number;
  sales: number;
  costBasis: number;
  averageCost: number;
  realisedGain: number;
}

interface QuantityValues {
  sold: number;
  purchased: number;
  total: number;
}

export interface Position {
  asset: Asset;
  moneyValues: MoneyValues;
  marketValue: MarketValue;
  quantityValues: QuantityValues;
  lastTradeDate: string;
}

interface Portfolio {
  code: string;
}
// Server side contract
interface HoldingContract {
  portfolio: Portfolio;
  positions: Position[];
}

// The payload we render in the UI
interface Holdings {
  portfolio: Portfolio;
  holdingGroups: HoldingGroup[];
}

// User defined grouping
interface HoldingGroup {
  group: string;
  total: number;
  positions: Position[];
}
