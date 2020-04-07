import Home from "./Home";
import ViewHoldings from "./holdings";
import { useParams } from "react-router-dom";
import Login from "./common/auth/Login";
import Logout from "./common/auth/Logout";
import Portfolios from "./portfolio/Portfolios";
import Registration from "./common/auth/Registration";
import { PortfolioEdit } from "./portfolio/PortfolioEdit";
import { DeletePortfolio } from "./portfolio/DeletePortfolio";
import Transactions from "./trns/Transactions";
import { TransactionEdit } from "./trns/TransactionEdit";

const __new__ = "new";
const RouteHoldings = (): JSX.Element => {
  const { portfolioId } = useParams();
  const pfId = portfolioId == undefined ? __new__ : portfolioId;
  return ViewHoldings(pfId);
};

const RoutePortfolio = (): JSX.Element => {
  const { portfolioId } = useParams();
  const pfId = portfolioId == undefined ? __new__ : portfolioId;
  return PortfolioEdit(pfId);
};

const RouteAssetTrnList = (): JSX.Element => {
  const { portfolioId, assetId } = useParams();
  const portfolio = portfolioId == undefined ? __new__ : portfolioId;
  const asset = assetId == undefined ? __new__ : assetId;
  return Transactions(portfolio, asset);
};

const RouteTrnEdit = (): JSX.Element => {
  const { portfolioId, trnId } = useParams();
  const pId = portfolioId == undefined ? __new__ : portfolioId;
  const tId = trnId == undefined ? __new__ : trnId;
  return TransactionEdit(pId, tId);
};

const RoutePortfolioDelete = (): JSX.Element => {
  const { portfolioId } = useParams();
  const pfId = portfolioId == undefined ? __new__ : portfolioId;
  return DeletePortfolio(pfId);
};

const ClientRoutes = [
  {
    path: "/",
    exact: true,
    component: Home,
  },
  {
    path: "/login",
    component: Login,
  },
  {
    path: "/logout",
    component: Logout,
  },
  {
    path: "/register",
    component: Registration,
  },
  {
    path: "/portfolios/:portfolioId/delete",
    component: RoutePortfolioDelete,
  },
  {
    path: "/portfolios/:portfolioId",
    component: RoutePortfolio,
  },
  {
    path: "/portfolios",
    component: Portfolios,
  },
  {
    path: "/holdings/:portfolioId",
    component: RouteHoldings,
  },
  {
    path: "/trns/:portfolioId/asset/:assetId",
    component: RouteAssetTrnList,
  },
  {
    path: "/trns/:portfolioId/:trnId",
    component: RouteTrnEdit,
  },
];

export default ClientRoutes;
