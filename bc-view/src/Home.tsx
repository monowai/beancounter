import React from "react";
import { Route, Switch, withRouter } from "react-router-dom";
import { withTranslation, WithTranslation } from "react-i18next";
import { RouteComponentProps } from "react-router";
import Loadable from "react-loadable";

const Holdings = Loadable({
  loader: () => import("./holdings/LayoutHoldings"),
  loading: () => null
});

const Error = Loadable({
  loader: () => import("./common/errors/Error"),
  loading: () => null
});

const Home = (props: WithTranslation & RouteComponentProps): JSX.Element => {
  const { history } = props;
  return (
    <div className="Home">
      BeanCounter Functions.
      <ul className="Home-resources">
        <li>
          <a href="/holdings" onClick={() => history.push("/")}>
            Holdings
          </a>
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
};

export default withTranslation()(withRouter(Home));
