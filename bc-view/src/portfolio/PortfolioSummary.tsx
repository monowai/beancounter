import React from "react";
import { Portfolio } from "../types/beancounter";

export default function PortfolioSummary(portfolio: Portfolio) {
  return <h1>Portfolio {portfolio.code}</h1>;
}
