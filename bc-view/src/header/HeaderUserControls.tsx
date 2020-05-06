import React from "react";
import { Link } from "react-router-dom";
import { useSystemUser } from "../user/hooks";
import { useKeycloak } from "@react-keycloak/razzle";

function HeaderUserControls(): React.ReactElement {
  const systemUser = useSystemUser();
  const [keycloak, initialized] = useKeycloak();
  if (keycloak && initialized && keycloak.authenticated && !systemUser.error) {
    const loginOrOut = systemUser.data.email ? (
      <div>{systemUser.data.email}</div>
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

    const authMenu = systemUser.data.email ? (
      <div className="navbar-dropdown">
        <Link to={"/register"} className="navbar-item">
          <span className="icon is-small">
            <i className="far fa-user-circle" />
          </span>
          <div>Register</div>
        </Link>
        <Link to={"/portfolios"} className="navbar-item">
          <span className="icon is-small">
            <i className="far fa-folder-open" />
          </span>
          <div>Portfolios</div>
        </Link>
        <div className="navbar-divider" />
        <Link to={"/logout"} className="navbar-item">
          <span className="icon is-small">
            <i className="fa fa-sign-out-alt" />
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
