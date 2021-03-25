import PortfolioStats from "../holdings/Stats";
import React from "react";
import { cleanup, render } from "@testing-library/react";
import "@testing-library/jest-dom/extend-expect";
import { Currency, Portfolio, SystemUser } from "../types/beancounter";
import { useKeycloak } from "@react-keycloak/ssr";

afterEach(cleanup);

const usd: Currency = { code: "USD", symbol: "$" };
jest.mock("@react-keycloak/ssr", () => ({
  useKeycloak: () => ({
    initialized: true,
    keycloak: { token: "abc" },
  }),
}));

describe("<PortfolioStats />", () => {
  jest.mock("react-i18next", () => ({
    useTranslation: () => ({ t: (key) => key }),
  }));

  it("should match snapshot", () => {
    const { keycloak, initialized } = useKeycloak();
    console.info(initialized + "/" + keycloak?.token);
    const owner: SystemUser = { active: true, email: "wow" };
    const portfolio: Portfolio = {
      id: "abc",
      code: "mike",
      name: "",
      currency: usd,
      base: usd,
      owner: owner,
    };
    const container = render(
      <table>
        <PortfolioStats portfolio={portfolio} />
      </table>
    );
    expect(container).toMatchSnapshot();
  });
});
