import React from "react";
import { withTranslation, WithTranslation } from "react-i18next";
import { RouteComponentProps, withRouter } from "react-router";
import { useKeycloak } from "@react-keycloak/web";

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const Home = (props: WithTranslation & RouteComponentProps): JSX.Element => {
  const { keycloak } = useKeycloak();
  if (keycloak) {
    const authState = keycloak?.authenticated ? (
      <span className="text-success">logged in</span>
    ) : (
      <span className="text-danger">not logged in</span>
    );

    return <div className="Home">Functions ({authState})</div>;
  }
  return <div>Preparing...</div>;
};

export default withTranslation()(withRouter(Home));
