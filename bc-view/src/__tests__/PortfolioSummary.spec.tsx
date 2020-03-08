import PortfolioStats from "../holdings/Stats";
import React from "react";
import { cleanup, render } from "@testing-library/react";
import "@testing-library/jest-dom/extend-expect";
import { Currency, Portfolio, SystemUser } from "../types/beancounter";

afterEach(cleanup);

const usd: Currency = { id: "us", code: "USD", symbol: "$" };

describe("<PortfolioStats />", () => {
  it("should match snapshot", () => {
    const owner: SystemUser = { active: true, email: "wow" };
    const portfolio: Portfolio = {
      id: "abc",
      code: "mike",
      name: "",
      currency: usd,
      base: usd,
      owner: owner
    };
    const container = render(
      <table>
        <PortfolioStats portfolio={portfolio} />
      </table>
    );
    expect(container).toMatchSnapshot();
  });
});
