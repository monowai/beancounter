import Axios from "axios";
import GroupedHoldings from "./GroupedHoldings";
import { HoldingContract } from "../types/beancounter";
import PortfolioSummary from "./PortfolioSummary";
import React from "react";
import "../App.css";

class LayoutHoldings extends React.Component {
  state = {
    holdingContract: (null as unknown) as HoldingContract,
    // hideEmpty: true, // Is it the layout that controls this - or is it the GroupedHoldings?
    loaded: false
  };

  render() {
    if (this.state.loaded) {
      // readability
      const portfolio = this.state.holdingContract.portfolio;

      if (portfolio) {
        return (
          <div>
            <PortfolioSummary {...portfolio} />
            <GroupedHoldings {...this.state.holdingContract} />
          </div>
        );
      }
    }
    return "Loading...";
  }

  componentDidMount() {
    Axios.get("http://localhost:9500/test") // Holding Contract
      .then(response => {
        // ToDo: Is it more efficient to convert the contract here for reuse or leave it to child components?
        this.setState({
          holdingContract: response.data,
          loaded: true
        });
      })
      .catch(error => {
        console.log(error);
      });
  }
}

export default LayoutHoldings;
