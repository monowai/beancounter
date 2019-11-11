import { HoldingFooter, HoldingHeader, WriteHoldings } from "./GroupedHoldings";
import PortfolioStats, { Stats } from "../portfolio/PortfolioStats";
import React, { useState } from "react";
import "../App.css";
import { computeHoldings } from "./computeHoldings";
import { description, GroupBy, ValuationCcy } from "./groupBy";
import { Holdings } from "../types/beancounter";
import useAxios from "axios-hooks";
import Totals from "./Totals";

const LayoutHoldings = (): JSX.Element => {
  const [holdingsValueIn] = useState<ValuationCcy>("PORTFOLIO");
  const [portfolioValueIn] = useState<ValuationCcy>("PORTFOLIO");
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
        <div>ToDo: Add Filters</div>
        <div className={"stats-container"}>
          <table>
            <PortfolioStats {...axiosResponse.data.data.portfolio} />
            {Stats(
              axiosResponse.data.data.portfolio,
              holdings.totals[portfolioValueIn]
            )}
          </table>
        </div>
        <div className={"group-header"}>Grouped by {description(groupBy)}</div>

        <div>
          <table className={"table is-striped is-hoverable"}>
            {Object.keys(holdings.holdingGroups).map(groupKey => {
              return (
                <React.Fragment key={groupKey}>
                  {HoldingHeader(groupKey)}
                  {WriteHoldings(
                    holdings.holdingGroups[groupKey],
                    holdingsValueIn
                  )}
                  {HoldingFooter(
                    holdings.holdingGroups[groupKey],
                    holdingsValueIn
                  )}
                </React.Fragment>
              );
            })}
            <Totals {...holdings} />
          </table>
        </div>
      </div>
    );
  }
  return <div>No Holdings...</div>;
};

export default LayoutHoldings;
