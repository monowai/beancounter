import React from "react";
import "../assets/styles.sass";
import { Portfolio } from "../types/beancounter";

export default function PortfolioSummary(portfolio: Portfolio): JSX.Element {
  return <div className="subTitle">
    Portfolio {portfolio.code} -{portfolio.currency.code}
  </div>;
}
