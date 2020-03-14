import Home from "./Home";
import ViewHoldings from "./holdings";
import { useParams } from "react-router-dom";
import Login from "./common/auth/Login";
import ErrorPage from "./common/errors/ErrorPage";
import Logout from "./common/auth/Logout";
import Portfolios from "./portfolio/Portfolios";
import Registration from "./common/auth/Registration";
import { ManagePortfolio } from "./portfolio/ManagePortfolio";
import { DeletePortfolio } from "./portfolio/DeletePortfolio";

const RouteHoldings = (): JSX.Element => {
  const { portfolioId } = useParams();
  if (portfolioId) return ViewHoldings(portfolioId);
  return ViewHoldings("portfolioId");
};

const RoutePortfolio = (): JSX.Element => {
  const { portfolioId } = useParams();
  if (portfolioId) return ManagePortfolio(portfolioId);
  return ManagePortfolio("new");
};

const RoutePortfolioDelete = (): JSX.Element => {
  const { portfolioId } = useParams();
  if (portfolioId) return DeletePortfolio(portfolioId);
  return DeletePortfolio("unknown");
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
    path: "/error",
    component: ErrorPage
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
