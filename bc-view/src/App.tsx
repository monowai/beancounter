import Home from "./Home";
import React from "react";
import "./i18nConfig";
import { Route, Switch } from "react-router-dom";
import ViewHoldings from "./holdings";

const App = (): JSX.Element => (
  <div>
    <div className={"columns"}>
      <div className={"column is-centered"}>Welcome to Bean Counter</div>
    </div>
    <Switch>
      <Route exact={true} path="/" component={Home} />
      <Route exact={true} path="/holdings" component={ViewHoldings} />
    </Switch>

  </div>
);

export default App;
