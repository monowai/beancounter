import React from "react";
import "./common/i18nConfig";
import { Switch, withRouter } from "react-router";
import ClientRoutes from "./clientRoutes";
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
      <Switch>{renderRoutes(ClientRoutes)}</Switch>
    </div>
  );
};

export default withRouter(App);
