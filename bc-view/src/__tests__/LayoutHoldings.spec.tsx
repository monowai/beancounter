import React from "react";
import { cleanup, render, waitForElement } from "@testing-library/react";
import "@testing-library/jest-dom/extend-expect";
import { renderHook } from "@testing-library/react-hooks";
import ViewHoldings from "../holdings";
import nock from "nock";
import { getApiUrl } from "../common/api";

afterEach(cleanup);
nock(getApiUrl())
  .get("/test/today")
  .replyWithFile(200, __dirname + "/contracts/test-holdings.json", {
    "Access-Control-Allow-Origin": "*",
    "Content-type": "application/json"
  });

nock(getApiUrl())
  .get("/zero/today")
  .replyWithFile(200, __dirname + "/contracts/zero-holdings.json", {
    "Access-Control-Allow-Origin": "*",
    "Content-type": "application/json"
  });

describe("<ViewHoldings />", () => {
  it("matches snapshot when holdings present", async () => {
    const { result, waitForNextUpdate } = renderHook(() => ViewHoldings("test"));

    await waitForNextUpdate();
    const container = render(result.current);
    waitForElement(() => container.getByText("USD"));
    expect(container).toMatchSnapshot();
  });

  it("matches snapshot for zero holdings", async () => {
    const { result, waitForNextUpdate } = renderHook(() => ViewHoldings("zero"));

    await waitForNextUpdate();
    const container = render(result.current);
    waitForElement(() => container.getByText("USD"));
    expect(container).toMatchSnapshot();
  });
});
