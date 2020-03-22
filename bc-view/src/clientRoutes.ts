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

const RouteTrnList = (): JSX.Element => {
  const { portfolioId, assetId } = useParams();
  const portfolio = portfolioId == undefined ? __new__ : portfolioId;
  const asset = assetId == undefined ? __new__ : assetId;
  return Transactions(portfolio, asset);
};

const RouteTrnManage = (): JSX.Element => {
  const { providerId, batchId, trnId } = useParams();
  const pId = providerId == undefined ? __new__ : providerId;
  const bId = batchId == undefined ? __new__ : batchId;
  const tId = trnId == undefined ? __new__ : trnId;
  return TransactionEdit(pId, bId, tId);
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
    path: "/portfolios/:portfolioId/delete",
    component: RoutePortfolioDelete
  },
  {
    path: "/portfolios/:portfolioId",
    component: RoutePortfolio
  },
  {
    path: "/portfolios",
    component: Portfolios
  },
  {
    path: "/holdings/:portfolioId",
    component: RouteHoldings
  },
  {
    path: "/trns/:providerId/:batchId/:trnId",
    component: RouteTrnManage
  },
  {
    path: "/trns/:portfolioId/:assetId",
    component: RouteTrnList
  }
];

export default ClientRoutes;
