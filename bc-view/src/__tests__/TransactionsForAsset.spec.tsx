import React from "react";
import { cleanup, render, screen, waitForElementToBeRemoved } from "@testing-library/react";
import "@testing-library/jest-dom/extend-expect";
import nock from "nock";
import { MemoryRouter } from "react-router";
import Trades from "../trns/Trades";

afterEach(cleanup);
afterAll(nock.cleanAll);

jest.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key) => key }),
}));

jest.mock("@react-keycloak/ssr", () => ({
  useKeycloak: () => ({
    initialized: true,
    keycloak: { token: "123" },
  }),
}));

nock("http://localhost", {
  reqheaders: {
    authorization: "Bearer 123",
  },
})
  .persist(true)
  .get("/bff/trns/test/asset/alphabet/trades")
  .replyWithFile(200, __dirname + "/__contracts__/trans-for-asset.json", {
    "Access-Control-Allow-Origin": "*",
    "Content-type": "application/json",
  })
  .get("/bff/assets/alphabet")
  .replyWithFile(200, __dirname + "/__contracts__/alphabet.json", {
    "Access-Control-Allow-Origin": "*",
    "Content-type": "application/json",
  });

describe("<Trades />", () => {
  it("trades for asset should match snapshot", async () => {
    const TestTrnForAsset = (): JSX.Element => {
      return Trades("test", "alphabet");
    };

    const { container } = render(
      <MemoryRouter initialEntries={["/"]} keyLength={0}>
        <TestTrnForAsset />
      </MemoryRouter>
    );
    await screen.findByTestId("loading");
    await waitForElementToBeRemoved(() => screen.getByTestId("loading"));
    expect(nock.isDone());
    await screen.findByText("BUY");
    expect(container).toMatchSnapshot();
  });
});
