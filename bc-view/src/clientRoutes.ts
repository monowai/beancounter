import Home from "./Home";
import ViewHoldings from "./holdings";
import { useParams } from "react-router-dom";
import Login from "./common/auth/Login";
import Logout from "./common/auth/Logout";
import Portfolios from "./portfolio/Portfolios";
import Registration from "./common/auth/Registration";
import { ManagePortfolio } from "./portfolio/ManagePortfolio";
import { DeletePortfolio } from "./portfolio/DeletePortfolio";
import Transactions from "./trns/Transactions";

const RouteHoldings = (): JSX.Element => {
  const { portfolioId } = useParams();
  const pfId = portfolioId == undefined ? "new" : portfolioId;
  return ViewHoldings(pfId);
};

const RoutePortfolio = (): JSX.Element => {
  const { portfolioId } = useParams();
  const pfId = portfolioId == undefined ? "new" : portfolioId;
  return ManagePortfolio(pfId);
};

const RouteTrnForAsset = (): JSX.Element => {
  const { portfolioId, assetId } = useParams();
  const pfId = portfolioId == undefined ? "new" : portfolioId;
  const asset = assetId == undefined ? "new" : assetId;
  return Transactions(pfId, asset);
};

const RoutePortfolioDelete = (): JSX.Element => {
  const { portfolioId } = useParams();
  const pfId = portfolioId == undefined ? "new" : portfolioId;
  return DeletePortfolio(pfId);
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
  },
  {
    path: "/trns/:portfolioId/:assetId",
    component: RouteTrnForAsset
  }
];

export default ClientRoutes;
