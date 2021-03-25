import React from "react";
import { useKeycloak } from "@react-keycloak/ssr";

const Logout = (): JSX.Element => {
  const { keycloak, initialized } = useKeycloak();
  if (initialized && keycloak != undefined) {
    console.debug("How to logout");
    keycloak.token = undefined;
    keycloak.authenticated = false;
  }
  return <div>Logged Out</div>;
};

export default Logout;
