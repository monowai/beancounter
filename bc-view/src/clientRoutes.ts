import Home from "./Home";
import ViewHoldings from "./holdings";
import { useParams } from "react-router-dom";
import Login from "./common/auth/Login";
import Logout from "./common/auth/Logout";
import Portfolios from "./portfolio/Portfolios";
import Registration from "./common/auth/Registration";
import { ManagePortfolio } from "./portfolio/ManagePortfolio";

const RouteHoldings = (): JSX.Element => {
  const { portfolioId } = useParams();
  if (portfolioId) return ViewHoldings(portfolioId);
  return ViewHoldings("portfolioId");
};

const RoutePortfolio = (): JSX.Element => {
  const { code } = useParams();
  if (code) return ManagePortfolio(code);
  return ManagePortfolio("code");
};

const ClientRoutes = [
  {
    path: "/",
    exact: true,
    component: Home
  },
  {
    path: "/login",
    component: Login
  },
  {
    path: "/logout",
    component: Logout
  },
  {
    path: "/register",
    component: Registration
  },
  {
    path: "/portfolios",
    component: Portfolios
  },
  {
    path: "/portfolio/:code",
    component: RoutePortfolio
  },
  {
    path: "/holdings/:portfolioId",
    component: RouteHoldings
  }
];

export default ClientRoutes;
