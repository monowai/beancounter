import React from "react";
import { useKeycloak } from "@react-keycloak/razzle";
import { LoginRedirect } from "./common/auth/Login";
import { Portfolios } from "./portfolio/Portfolios";

const Home = (): JSX.Element => {
  const [keycloak] = useKeycloak();
  if (keycloak) {
    if (!keycloak.token) {
      return <LoginRedirect />;
    }
    return <Portfolios />;
  }
  return <div>Initializing...</div>;
};

export default Home;
