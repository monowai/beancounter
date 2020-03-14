import { useKeycloak } from "@react-keycloak/razzle";
import React from "react";
import { LoginRedirect } from "./common/auth/Login";
import { Portfolios } from "./portfolio/Portfolios";

export function Landed(): JSX.Element {
  const [keycloak] = useKeycloak();

  if (keycloak) {
    if (!keycloak.authenticated) {
      return <LoginRedirect />;
    }
    return <Portfolios />;
  }
  return <div>Loading...</div>;
}
