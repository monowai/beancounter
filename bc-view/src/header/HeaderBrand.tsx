import React from "react";

function HeaderBrand(): React.ReactElement {
  return (
    <div className="navbar-brand">
      <a className="navbar-item">
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
