import React from "react";
import { cleanup, render, screen, waitForElementToBeRemoved } from "@testing-library/react";
import "@testing-library/jest-dom/extend-expect";
import ViewHoldings from "../holdings";
import nock from "nock";
import { MemoryRouter } from "react-router";

afterEach(cleanup);
afterAll(nock.cleanAll);

jest.mock("@react-keycloak/ssr", () => ({
  useKeycloak: () => ({
    initialized: true,
    keycloak: { token: "abc" },
  }),
}));

nock("http://localhost", {
  reqheaders: {
    Authorization: "Bearer abc",
  },
})
  .persist(true)
  .get("/bff/test/today")
  .replyWithFile(200, __dirname + "/__contracts__/test-holdings.json", {
    "Access-Control-Allow-Origin": "*",
    "Content-type": "application/json",
  })
  .get("/bff/zero/today")
  .replyWithFile(200, __dirname + "/__contracts__/zero-holdings.json", {
    "Access-Control-Allow-Origin": "*",
    "Content-type": "application/json",
  });

describe("<ViewHoldings />", () => {
  it("matches snapshot when getData present", async () => {
    const TestHoldings = (): JSX.Element => {
      return ViewHoldings("test");
    };
    const { container } = render(
      <MemoryRouter initialEntries={["/portfolios"]} keyLength={0}>
        <TestHoldings />
      </MemoryRouter>
    );
    await screen.findByTestId("loading");
    await waitForElementToBeRemoved(() => screen.getByTestId("loading"));
    expect(nock.isDone());
    await screen.findByText("USD");
    expect(container).toMatchSnapshot();
  });

  it("matches snapshot for zero getData", async () => {
    const ZeroHoldings = (): JSX.Element => {
      return ViewHoldings("zero");
    };
    const { container } = render(<ZeroHoldings />);
    expect(nock.isDone());
    await screen.findByTestId("dropzone");
    expect(container).toMatchSnapshot();
  });
});
