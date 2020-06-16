import React from "react";
import { cleanup, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom/extend-expect";
import nock from "nock";
import Portfolios from "../portfolio/Portfolios";
import { MemoryRouter } from "react-router";

afterEach(cleanup);

const bff = "http://localhost";
nock(bff, {
  reqheaders: {
    authorization: "Bearer undefined",
  },
})
  .get("/bff/portfolios")
  .replyWithFile(200, __dirname + "/__contracts__/portfolios.json", {
    "Access-Control-Allow-Origin": "*",
    "Content-type": "application/json",
  });

describe("<Portfolios />", () => {
  it("should match snapshot", async () => {
    const { container } = render(
      <MemoryRouter initialEntries={["/"]} keyLength={0}>
        <Portfolios />
      </MemoryRouter>
    );
    await screen.findByText("TEST");
    //await waitFor(() => getByText("TEST"));
    expect(nock.isDone());
    expect(container).toMatchSnapshot();
  });
});
