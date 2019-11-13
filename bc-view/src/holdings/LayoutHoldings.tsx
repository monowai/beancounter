import { HoldingFooter, HoldingHeader, HoldingRows } from "./GroupedHoldings";
import React, { useState } from "react";
import "../App.css";
import { computeHoldings } from "./computeHoldings";
import { description, GroupBy, ValuationCcy } from "./groupBy";
import { Holdings } from "../types/beancounter";
import useAxios from "axios-hooks";
import Totals from "./Totals";
import StatsHeader, { StatsRow } from "../portfolio/PortfolioStats";

const LayoutHoldings = (): JSX.Element => {
  const [holdingsValueIn, setHoldingsValueIn] = useState<ValuationCcy>(
    "PORTFOLIO"
  );
  // const [portfolioValueIn, setPortfolioValueIn] = useState<ValuationCcy>("BASE");
  const [hideEmpty] = useState<boolean>(true);
  const [groupBy] = useState<GroupBy>(GroupBy.MARKET_CURRENCY);
  const [axiosResponse] = useAxios("http://localhost:9500/api/" + "test");
  if (axiosResponse.loading) {
    return <p>Loading...</p>;
  }
  if (axiosResponse.error) {
    return <p>{axiosResponse.error.message}!</p>;
  }
  if (axiosResponse.data) {
    const holdings = computeHoldings(
      axiosResponse.data.data,
      hideEmpty,
      holdingsValueIn,
      groupBy
    ) as Holdings;

    return (
      <div className={"page-box"}>
        <div>
          <select
            defaultValue={holdingsValueIn}
            onChange={(e: React.FormEvent<HTMLSelectElement>) => {
              const value: ValuationCcy = e.currentTarget.value as ValuationCcy;
              setHoldingsValueIn(value);
            }}
          >
            <option value="PORTFOLIO">Portfolio</option>
            <option value="BASE">Base</option>
            <option value="TRADE">Trade</option>
          </select>
        </div>
        <div className={"stats-container"}>
          <table>
            <StatsHeader portfolio={axiosResponse.data.data.portfolio} />
            <StatsRow
              portfolio={axiosResponse.data.data.portfolio}
              moneyValues={holdings.totals}
              valueIn={holdingsValueIn}
            />
          </table>
        </div>
        <div className={"group-header"}>Grouped by {description(groupBy)}</div>

        <div>
          <table className={"table is-striped is-hoverable"}>
            {Object.keys(holdings.holdingGroups).map(groupKey => {
              return (
                <React.Fragment key={groupKey}>
                  <HoldingHeader groupKey={groupKey} />
                  <HoldingRows
                    holdingGroup={holdings.holdingGroups[groupKey]}
                    valueIn={holdingsValueIn}
                  />
                  <HoldingFooter
                    holdingGroup={holdings.holdingGroups[groupKey]}
                    valueIn={holdingsValueIn}
                  />
                </React.Fragment>
              );
            })}
            <Totals holdings={holdings} valueIn={holdingsValueIn} />
          </table>
        </div>
      </div>
    );
  }
  return <div>No Holdings...</div>;
};

export default LayoutHoldings;
