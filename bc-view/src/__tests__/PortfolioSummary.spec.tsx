import PortfolioSummary from "../portfolio/PortfolioSummary";
import React from "react";
import { cleanup, render } from "@testing-library/react";
import "@testing-library/jest-dom/extend-expect";
import { Currency } from "../types/beancounter";

afterEach(cleanup);

const usd: Currency = { id: "us", code: "USD", symbol: "$" };

describe("<PortfolioSummary />", () => {
  it("should match snapshot", () => {
    const container = render(
      <table>
        <PortfolioSummary code={"mike"} currency={usd} base={usd} />
      </table>
    );
    expect(container).toMatchSnapshot();
  });
});
