import React from "react";
import { useKeycloak } from "@react-keycloak/web";
import { Link } from "react-router-dom";
import { useSystemUser } from "../profile/hooks";

function HeaderUserControls(): React.ReactElement {
  const systemUser = useSystemUser();
  const { keycloak } = useKeycloak();
  if (keycloak) {
    const loginOrOut = keycloak.authenticated ? (
      <div>{systemUser.email}</div>
    ) : (
      <div>
        <span className="icon is-small">
          <i className="fa fa-sign-in" />
        </span>
        <Link className={"link"} to={"/login"}>
          {" "}
          Sign In
        </Link>
      </div>
    );

    const authMenu = keycloak.authenticated ? (
      <div className="navbar-dropdown">
        <Link to={"/register"} className="navbar-item">
          <span className="icon is-small">
            <i className="fa fa-user-circle" />
          </span>
          <div>Register</div>
        </Link>
        <Link to={"/portfolios"} className="navbar-item">
          <span className="icon is-small">
            <i className="fa fa-folder-o" />
          </span>
          <div>Portfolios</div>
        </Link>
        <div className="navbar-divider" />
        <Link to={"/logout"} className="navbar-item">
          <span className="icon is-small">
            <i className="fa fa-sign-out" />
          </span>
          <div>Sign Out</div>
        </Link>
      </div>
    ) : (
      <div />
    );

    return (
      <div className="navbar-end">
        <div className="navbar-item has-dropdown is-hoverable">
          <div className="navbar-link">
            {authMenu}
            {loginOrOut}
          </div>
        </div>
      </div>
    );
  }
  return <div>Preparing...</div>;
}

export default HeaderUserControls;
