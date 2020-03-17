import Home from "./Home";
import ViewHoldings from "./holdings";
import { useParams } from "react-router-dom";
import Login from "./common/auth/Login";
import Logout from "./common/auth/Logout";
import Portfolios from "./portfolio/Portfolios";
import Registration from "./common/auth/Registration";
import { ManagePortfolio } from "./portfolio/ManagePortfolio";
import { DeletePortfolio } from "./portfolio/DeletePortfolio";

const RouteHoldings = (): JSX.Element => {
  const { portfolioId } = useParams();
  return ViewHoldings(portfolioId == undefined ? "new" : portfolioId);
};

const RoutePortfolio = (): JSX.Element => {
  const { portfolioId } = useParams();
  return ManagePortfolio(portfolioId == undefined ? "new" : portfolioId);
};

const RoutePortfolioDelete = (): JSX.Element => {
  const { portfolioId } = useParams();
  return DeletePortfolio(portfolioId == undefined ? "unknown" : portfolioId);
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
    path: "/portfolio/delete/:portfolioId",
    component: RoutePortfolioDelete
  },
  {
    path: "/portfolio/:portfolioId",
    component: RoutePortfolio
  },
  {
    path: "/holdings/:portfolioId",
    component: RouteHoldings
  }
];

export default ClientRoutes;
