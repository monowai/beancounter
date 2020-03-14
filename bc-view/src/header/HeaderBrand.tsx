import React from "react";
import { useHistory } from "react-router";
import { useTranslation } from "react-i18next";

function HeaderBrand(): React.ReactElement {
  const history = useHistory();
  const { t } = useTranslation();
  return (
    <div className="navbar-brand">
      <a
        className="navbar-item"
        onClick={() => {
          history.push("/");
        }}
      >
        {t("app")}
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
