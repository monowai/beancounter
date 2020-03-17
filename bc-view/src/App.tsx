import React from "react";
import { Switch, withRouter } from "react-router";
import ClientRoutes from "./clientRoutes";
import { renderRoutes } from "react-router-config";
import Header from "./header/Header";

const App = (): JSX.Element => {
  return (
    <div className={"page.box"}>
      <Header />
      <Switch>{renderRoutes(ClientRoutes)}</Switch>
    </div>
  );
};

export default withRouter(App);
