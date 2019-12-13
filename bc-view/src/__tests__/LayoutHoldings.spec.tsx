import React from "react";
import { cleanup, render, waitForElement } from "@testing-library/react";
import "@testing-library/jest-dom/extend-expect";
import ViewHoldings from "../holdings";
import nock from "nock";

afterEach(cleanup);

nock("http://localhost:9500")
  .get("/api/test")
  .replyWithFile(200, __dirname + "/contracts/holdings.json", {
    "Access-Control-Allow-Origin": "*",
    "Content-type": "application/json"
  });

describe("<ViewHoldings />", () => {
  it("should match snapshot", async () => {
    const container = render(<ViewHoldings />);

    await waitForElement(() => container.getByText("NZD"));
    expect(container).toMatchSnapshot();
  });
});
