import GroupedHoldings from "./GroupedHoldings";
import PortfolioSummary from "../portfolio/PortfolioSummary";
import React from "react";
import "../App.css";
import useAxios from "axios-hooks";

const LayoutHoldings = () => {
  const [{ data, loading, error }, refetch] = useAxios(
    "http://localhost:9500/api/test"
  );

  if (loading) {
    return <p>Loading...</p>;
  }
  if (error) {
    return <p>Error!</p>;
  }
  if (data) {
    const portfolio = data.data.portfolio;
    return (
      <div>
        <PortfolioSummary {...portfolio} />
        <GroupedHoldings {...data.data} />
      </div>
    );
  }
  return <div>Umm...</div>;
};

export default LayoutHoldings;
