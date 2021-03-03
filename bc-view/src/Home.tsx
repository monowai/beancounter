import React from "react";
import { useKeycloak } from "@react-keycloak/ssr";
import { Portfolios } from "./portfolio/Portfolios";
import Login from "./common/auth/Login";

const Home = (): JSX.Element => {
  const { keycloak, initialized } = useKeycloak();
  if (initialized && keycloak?.token) {
    return <Portfolios />;
  }
  return <Login />;
  //return <div>Not logged in</div>;
};

export default Home;
