import PortfolioSummary from "../portfolio/PortfolioSummary";
import React from "react";
import { cleanup, render } from "@testing-library/react/pure";
import { Currency } from "../types/beancounter";

afterEach(cleanup);

const usd: Currency = { id: "us", code: "USD", symbol: "$" };

describe("<PortfolioSummary />", () => {
  it("should match snapshot", () => {
    const component = render(
      <PortfolioSummary code={"code"} currency={usd} base={usd} />
    );
    expect(component).toMatchSnapshot();
  });
});
