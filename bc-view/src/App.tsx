import React from "react";
import "./i18nConfig";
import { Switch } from "react-router-dom";
import { renderRoutes } from "react-router-config";
import Routes from "./routes";

const App = (): JSX.Element => (
  <div>
    <div className={"columns"}>
      <div className={"column is-centered"}>Welcome to Bean Counter</div>
    </div>
    <Switch>{renderRoutes(Routes)}</Switch>
  </div>
);

export default App;
