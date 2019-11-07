import { HoldingFooter, HoldingHeader, WriteHoldings } from "./GroupedHoldings";
import PortfolioSummary from "../portfolio/PortfolioSummary";
import React, { useState } from "react";
import "../App.css";
import { computeHoldings } from "./computeHoldings";
import { GroupBy, ValuationCcy } from "./enums";
import { Holdings } from "../types/beancounter";
import useAxios from "axios-hooks";
import Totals from "./Totals";

const LayoutHoldings = (): JSX.Element => {
  const [valueIn] = useState<ValuationCcy>("PORTFOLIO");
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
      valueIn,
      groupBy
    ) as Holdings;

    const holdingsByGroup = Object.keys(holdings.holdingGroups).map((groupKey, index) => {
      return (
        <React.Fragment key={index}>
          {HoldingHeader(groupKey)}
          {WriteHoldings(holdings.holdingGroups[groupKey], holdings.valueIn)}
          {HoldingFooter(holdings.holdingGroups[groupKey], holdings.valueIn)}
        </React.Fragment>
      );
    });

    return (

        <table className={"table-container is-striped is-hoverable"}>
          <PortfolioSummary {...axiosResponse.data.data.portfolio} />
          {holdingsByGroup}
          <Totals {...holdings} />
        </table>

    );
  }
  return <div>No Holdings...</div>;
};

export default LayoutHoldings;
