import GroupHoldings from "./GroupedHoldings";
import PortfolioSummary from "../portfolio/PortfolioSummary";
import React, { useState } from "react";
import "../App.css";
import useAxios from "axios-hooks";
import { groupHoldings } from "./groupHoldings";
import { GroupBy, ValuationCcy } from "./enums";
import { Holdings } from "../types/beancounter";

const LayoutHoldings = (): JSX.Element => {
  const [valueIn, setValueIn]= useState<ValuationCcy>("PORTFOLIO");
  const [hideEmpty, setHideEmpty]= useState<boolean>(true);
  const [groupBy, setGroupBy] = useState<GroupBy>(GroupBy.MARKET_CURRENCY);
  const [{ data, loading, error }] = useAxios("http://localhost:9500/api/test");

  if (loading) {
    return <p>Loading...</p>;
  }
  if (error) {
    return <p>{error.message}!</p>;
  }
  if (data) {
    const portfolio = data.data.portfolio;
    const holdings = groupHoldings(
      data.data,
      hideEmpty,
      valueIn,
      groupBy
    ) as Holdings;

    return (
      <div>
        <PortfolioSummary {...portfolio} />
        <GroupHoldings {...holdings}  />
      </div>
    );
  }
  return <div>Umm...</div>;
};

export default LayoutHoldings;
