import React from "react";
import { useHistory } from "react-router";

function HeaderBrand(): React.ReactElement {
  const history = useHistory();
  return (
    <div className="navbar-brand">
      <a
        className="navbar-item"
        onClick={() => {
          history.push("/");
        }}
      >
        BeanCounter$
        {/*<img src={Logo} />*/}
      </a>
      <div className="navbar-burger burger">
        <span></span>
        <span></span>
        <span></span>
      </div>
    </div>
  );
}

export default HeaderBrand;
