import { HoldingFooter, HoldingHeader, WriteHoldings } from "./GroupedHoldings";
import PortfolioStats, { Stats } from "../portfolio/PortfolioStats";
import React, { useState } from "react";
import "../App.css";
import { computeHoldings } from "./computeHoldings";
import { GroupBy, ValuationCcy } from "./enums";
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

    const holdingsByGroup = Object.keys(holdings.holdingGroups).map(
      (groupKey, index) => {
        return (
          <React.Fragment key={index}>
            {HoldingHeader(groupKey)}
            {WriteHoldings(holdings.holdingGroups[groupKey], holdingsValueIn)}
            {HoldingFooter(holdings.holdingGroups[groupKey], holdingsValueIn)}
          </React.Fragment>
        );
      }
    );

    return (
      <div>
        <div>ToDo: Add Filters</div>
        <div className={"stats-container"}>
          <table>
            <PortfolioStats {...axiosResponse.data.data.portfolio} />
            <Stats {...holdings.totals[portfolioValueIn]} />
          </table>
        </div>
        <div className={"holdings-container"}>
          <table className={"table holding-table is-striped is-hoverable"}>
            {holdingsByGroup}
            <Totals {...holdings} />
          </table>
        </div>
      </div>
    );
  }
  return <div>No Holdings...</div>;
};

export default LayoutHoldings;
