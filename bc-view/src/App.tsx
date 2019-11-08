import Home from "./Home";
import React from "react";
import "./i18nConfig";
import { Route, Switch } from "react-router-dom";
import LayoutHoldings from "./holdings/LayoutHoldings";

const App = (): JSX.Element => (
  <Switch>
    <Route exact={true} path="/" component={Home} />
    <Route exact={true} path="/holdings" component={LayoutHoldings} />
  </Switch>
);

export default App;
