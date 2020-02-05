import React from "react";
import { Link } from "react-router-dom";
import { withTranslation, WithTranslation } from "react-i18next";
import { RouteComponentProps, withRouter } from "react-router";

const Home = (props: WithTranslation & RouteComponentProps): JSX.Element => {
  const { history } = props;
  return (
    <div className="Home">
      BeanCounter Functions.
      <ul className="Home-resources">
        <li>
          <Link to={"/holdings/FT-vUCChRwOXDP7itcp5Kw"}>MKH</Link>
        </li>
      </ul>
    </div>
  );
};

export default withTranslation()(withRouter(Home));
