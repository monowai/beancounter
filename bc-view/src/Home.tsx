import React from "react";
import { withTranslation, WithTranslation } from "react-i18next";
import { RouteComponentProps, withRouter } from "react-router";
import { useKeycloak } from "@react-keycloak/razzle";
import { Landed } from "./Landed";

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const Home = (props: WithTranslation & RouteComponentProps): JSX.Element => {
  const [keycloak] = useKeycloak();

  if (keycloak) {
    return <Landed />;
  }
  return <div>Initializing...</div>;
};

export default withTranslation()(withRouter(Home));
