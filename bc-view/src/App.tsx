import Home from "./Home";
import React from "react";
import "./i18nConfig";
import { Route, Switch, useParams } from "react-router-dom";
import ViewHoldings from "./holdings";

const Holdings = (): JSX.Element => {
  const { portfolioId } = useParams();
  return ViewHoldings(portfolioId);
};

const App = (): JSX.Element => (
  <div>
    <div className={"columns"}>
      <div className={"column is-centered"}>Welcome to Bean Counter</div>
    </div>
    <Switch>
      <Route exact={true} path="/" component={Home} />
      <Route path="/holdings/:portfolioId" component={Holdings} />
    </Switch>
  </div>
);

export default App;
