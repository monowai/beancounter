import React from "react";
import { Link } from "react-router-dom";
import { withTranslation, WithTranslation } from "react-i18next";
import { RouteComponentProps, withRouter } from "react-router";
import { useKeycloak } from "@react-keycloak/web";
//
const Home = (props: WithTranslation & RouteComponentProps): JSX.Element => {
  const { history } = props;
  const { keycloak } = useKeycloak();

  if (keycloak) {
    const authState = keycloak?.authenticated ? (
      <span className="text-success">logged in</span>
    ) : (
      <span className="text-danger">not logged in</span>
    );

    return (
      <div className="Home">
        Functions ({authState})
        <ul className="Home-resources">
          <li>
            <Link onClick={() => history.push("/")} to={"/login"}>
              Login
            </Link>
          </li>
          <li>
            <Link onClick={() => history.push("/")} to={"/logout"}>
              Logout
            </Link>
          </li>
          <li>
            <Portfolios />
          </li>
        </ul>
      </div>
    );
  }
  return <div>Preparing...</div>;
};

export default withTranslation()(withRouter(Home));
