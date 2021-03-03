import App from "../App";
import React from "react";
import ReactDOM from "react-dom";
import { MemoryRouter } from "react-router";

jest.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key) => key }),
}));

jest.mock("@react-keycloak/ssr", () => ({
  useKeycloak: () => ({ initialized: () => true }),
}));

describe("<App />", () => {
  test("renders without exploding", () => {
    const div = document.createElement("div");
    ReactDOM.render(
      <MemoryRouter>
        <App />
      </MemoryRouter>,
      div
    );
  });
});
