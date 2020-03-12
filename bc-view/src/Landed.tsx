import { useKeycloak } from "@react-keycloak/web";
import { useSystemUser } from "./profile/hooks";
import React from "react";
import { LoginRedirect } from "./common/auth/Login";
import { Portfolios } from "./portfolio/Portfolios";

export function Landed(): JSX.Element {
  const { keycloak } = useKeycloak();
  const systemUser = useSystemUser();

  if (keycloak && systemUser) {
    if (!keycloak.authenticated) {
      return <LoginRedirect />;
    } else if (systemUser.email === "loading") {
      return <div className="Home">Loading...</div>;
    }
    return <Portfolios />;
  }
  return <div>Loading...</div>;
}
