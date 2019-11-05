import React from "react";
import { Route, Switch } from "react-router-dom";
import Loadable from "react-loadable";

const Holdings = Loadable({
  loader: () => import("./holdings/LayoutHoldings"),
  loading: () => null
});

const Error = Loadable({
  loader: () => import("./common/errors/Error"),
  loading: () => null
});

function Home(): JSX.Element {
  return (
    <div className="Home">
      BeanCounter Functions
      <ul className="Home-resources">
        <li>
          <a href="/holdings">Holdings</a>
        </li>
      </ul>
      <Switch>
        {/*<Route exact={true} path="/" component={Home} />*/}
        <Route path="/holdings" component={Holdings} />
        {/*<Route path="/status" component={Status} />*/}
        <Route path="/error" component={Error} />
      </Switch>
    </div>
  );
}

export default Home;
