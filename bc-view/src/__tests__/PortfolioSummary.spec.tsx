import PortfolioStats from "../portfolio/Stats";
import React from "react";
import { cleanup, render } from "@testing-library/react";
import "@testing-library/jest-dom/extend-expect";
import { Currency, Portfolio } from "../types/beancounter";

afterEach(cleanup);

const usd: Currency = { id: "us", code: "USD", symbol: "$" };

describe("<PortfolioStats />", () => {
  it("should match snapshot", () => {
    const portfolio: Portfolio = { code: "mike", currency: usd, base: usd };
    const container = render(
      <table>
        <PortfolioStats portfolio={portfolio} />
      </table>
    );
    expect(container).toMatchSnapshot();
  });
});
