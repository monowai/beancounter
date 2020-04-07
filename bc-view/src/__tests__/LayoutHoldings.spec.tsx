import React from "react";
import { render, waitForElement } from "@testing-library/react";
import "@testing-library/jest-dom/extend-expect";
import ViewHoldings from "../holdings";
import nock from "nock";
import { MemoryRouter } from "react-router";

const bff = "http://localhost";

nock(bff, {
  reqheaders: {
    authorization: "Bearer undefined",
  },
})
  .get("/bff/test/today")
  .replyWithFile(200, __dirname + "/__contracts__/test-holdings.json", {
    "Access-Control-Allow-Origin": "*",
    "Content-type": "application/json",
  })
  .get("/bff/zero/today")
  .replyWithFile(200, __dirname + "/__contracts__/zero-holdings.json", {
    "Access-Control-Allow-Origin": "*",
    "Content-type": "application/json",
  })
  .log(console.log);

describe("<ViewHoldings />", () => {
  it("matches snapshot when getData present", async () => {
    const TestHoldings = (): JSX.Element => {
      return ViewHoldings("test");
    };
    const { getByText, container } = render(
      <MemoryRouter initialEntries={["/"]} keyLength={0}>
        <TestHoldings />
      </MemoryRouter>
    );
    await waitForElement(() => getByText("USD"));
    expect(nock.isDone());
    expect(container).toMatchSnapshot();
  });

  it("matches snapshot for zero getData", async () => {
    const ZeroHoldings = (): JSX.Element => {
      return ViewHoldings("zero");
    };
    const { getByText, container } = render(<ZeroHoldings />);
    await waitForElement(() => getByText("Value In"));
    expect(nock.isDone());
    expect(container).toMatchSnapshot();
  });
});
