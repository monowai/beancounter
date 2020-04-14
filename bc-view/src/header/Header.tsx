import React from "react";
import HeaderBrand from "./HeaderBrand";
import HeaderUserControls from "./HeaderUserControls";

export default function Header(): React.ReactElement {
  return (
    <header>
      <nav className="navbar">
        {""}
        <HeaderBrand />
        <div className="navbar-menu">
          {""}
          <div className="navbar-start">
            {""}
            <div className="navbar-item">
              <i className="fas fa-euro-sign"></i>
              <i className="fas fa-dollar-sign"></i>
              <i className="fas fa-pound-sign"></i>
              <small> Wealth management for the rest of us</small>
            </div>
          </div>
          <HeaderUserControls />
        </div>
      </nav>
    </header>
  );
}
