import Home from "./Home";
import React from "react";
import "./i18nConfig";
import { Route, Switch } from "react-router-dom";
import Layout from "./holdings";

const App = (): JSX.Element => (
  <Switch>
    <Route exact={true} path="/" component={Home} />
    <Route exact={true} path="/holdings" component={Layout} />
  </Switch>
);

export default App;
