import React from "react";
import { withKeycloak } from "@react-keycloak/razzle";
import { Landed } from "./Landed";
import { KeycloakInstance } from "keycloak-js";

// eslint-disable-next-line @typescript-eslint/no-unused-vars
//const Home = (props: WithTranslation & RouteComponentProps): JSX.Element => {
class Home extends React.Component<{
  keycloak: KeycloakInstance;
  keycloakInitialized: boolean;
  isServer: boolean;
}> {
  render(): JSX.Element {
    const { keycloak } = this.props;
    // const [keycloak] = useKeycloak();
    if (keycloak) {
      return <Landed />;
    }
    return <div>Initializing...</div>;
  }
}

export default withKeycloak(Home);
