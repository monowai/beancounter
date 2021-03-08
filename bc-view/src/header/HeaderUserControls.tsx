import React from "react";
import { Link } from "react-router-dom";
import { useSystemUser } from "../user/hooks";

function HeaderUserControls(): React.ReactElement {
  const systemUser = useSystemUser();
  const loginOrOut = systemUser.data.email ? <div>{systemUser.data.email}</div> : <div />;

  const loggedIn = systemUser.data.email ? (
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
          {loggedIn}
          {loginOrOut}
        </div>
      </div>
    </div>
  );
}

export default HeaderUserControls;
