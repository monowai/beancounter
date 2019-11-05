import GroupHoldings from "./GroupedHoldings";
import PortfolioSummary from "../portfolio/PortfolioSummary";
import React, { useState } from "react";
import "../App.css";
import { prepHoldings } from "./prepHoldings";
import { GroupBy, ValuationCcy } from "./enums";
import { Holdings } from "../types/beancounter";
import useAxios from "axios-hooks";

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
    const holdings = prepHoldings(
      axiosResponse.data.data,
      hideEmpty,
      valueIn,
      groupBy
    ) as Holdings;

    return (
      <div>
        <PortfolioSummary {...axiosResponse.data.data.portfolio} />
        <GroupHoldings {...holdings} />
      </div>
    );
  }
  return <div>No Holdings...</div>;
};

export default LayoutHoldings;
