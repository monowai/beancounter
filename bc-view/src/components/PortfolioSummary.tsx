import React from "react";
import { Portfolio } from "../types/beancounter";

class PortfolioSummary extends React.Component<Portfolio> {
  render() {
    return <h1>Portfolio {this.props.code}</h1>;
  }
}

export default PortfolioSummary;
