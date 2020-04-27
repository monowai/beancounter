import React from "react";
import { cleanup, render } from "@testing-library/react";
import "@testing-library/jest-dom/extend-expect";
import nock from "nock";
import { MemoryRouter } from "react-router";
import Transactions from "../trns/Transactions";
import { waitFor } from "@testing-library/dom";

afterEach(cleanup);

const bff = "http://localhost";
nock(bff, {
  reqheaders: {
    authorization: "Bearer undefined",
  },
})
  .get("/bff/trns/test/asset/alphabet")
  .replyWithFile(200, __dirname + "/__contracts__/trans-for-asset.json", {
    "Access-Control-Allow-Origin": "*",
    "Content-type": "application/json",
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

describe("<Transactions />", () => {
  it("should match snapshot", async () => {
    const TestTrnForAsset = (): JSX.Element => {
      return Transactions("test", "alphabet");
    };

    const { getByText, container } = render(
      <MemoryRouter initialEntries={["/"]} keyLength={0}>
        <TestTrnForAsset />
      </MemoryRouter>
    );
    await waitFor(() => getByText("BUY"));
    expect(nock.isDone());
    expect(container).toMatchSnapshot();
  });
});
