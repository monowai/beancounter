import React from "react";
import { cleanup, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom/extend-expect";
import nock from "nock";
import { MemoryRouter } from "react-router";
import Trades from "../trns/Trades";

afterEach(cleanup);

const bff = "http://localhost";
jest.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key) => key })
}));

nock(bff, {
  reqheaders: {
    authorization: "Bearer undefined"
  }
})
  .get("/bff/trns/test/asset/alphabet/trades")
  .replyWithFile(200, __dirname + "/__contracts__/trans-for-asset.json", {
    "Access-Control-Allow-Origin": "*",
    "Content-type": "application/json"
  });

nock(bff, {
  reqheaders: {
    authorization: "Bearer undefined",
  },
})
  .get("/bff/assets/alphabet")
  .replyWithFile(200, __dirname + "/__contracts__/alphabet.json", {
    "Access-Control-Allow-Origin": "*",
    "Content-type": "application/json",
  });

describe("<Trades />", () => {
  it("trades should match snapshot", async () => {
    const TestTrnForAsset = (): JSX.Element => {
      return Trades("test", "alphabet");
    };

    const { container } = render(
      <MemoryRouter initialEntries={["/"]} keyLength={0}>
        <TestTrnForAsset />
      </MemoryRouter>
    );
    await screen.findByText("BUY");
    expect(nock.isDone());
    expect(container).toMatchSnapshot();
  });
});
