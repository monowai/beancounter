import React from "react";
import "./common/i18nConfig";
import { Switch } from "react-router";
import Routes from "./routes";
import { renderRoutes } from "react-router-config";
import { Link } from "react-router-dom";

const App = (): JSX.Element => {
  return (
    <div>
      <div className={"columns"}>
        <div className={"column is-centered"}>
          Welcome to <Link to={"/"}>Bean Counter</Link>
        </div>
      </div>
      <Switch>{renderRoutes(Routes)}</Switch>
    </div>
  );
};

export default App;
