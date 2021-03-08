import React from "react";
import { useKeycloak } from "@react-keycloak/ssr";
import { Redirect } from "react-router";

const Home = (): JSX.Element => {
  const { keycloak } = useKeycloak();
  if (keycloak?.authenticated) {
    return <Redirect to="/portfolios" />;
  }
  return <Redirect to="/login" />;
};

export default Home;
