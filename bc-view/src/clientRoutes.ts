import Home from "./Home";
import ViewHoldings from "./holdings";
import { useParams } from "react-router-dom";
import { Login } from "./user/Login";
import Logout from "./user/Logout";
import Portfolios from "./portfolio/Portfolios";
import Registration from "./user/Registration";
import { PortfolioEdit } from "./portfolio/PortfolioEdit";
import { DeletePortfolio } from "./portfolio/DeletePortfolio";
import Trades from "./trns/Trades";
import { TransactionEdit } from "./trns/TransactionEdit";
import Events from "./trns/Events";

const __new__ = "new";
const RouteHoldings = (): JSX.Element => {
  const { portfolioId } = useParams<{
    portfolioId: string;
  }>();
  const pfId = portfolioId == undefined ? __new__ : portfolioId;
  return ViewHoldings(pfId);
};

const RoutePortfolio = (): JSX.Element => {
  const { portfolioId } = useParams<{
    portfolioId: string;
  }>();
  const pfId = portfolioId == undefined ? __new__ : portfolioId;
  return PortfolioEdit(pfId);
};

const RouteTradeList = (): JSX.Element => {
  const { portfolioId, assetId } = useParams<{
    portfolioId: string;
    assetId: string;
  }>();
  const portfolio = portfolioId == undefined ? __new__ : portfolioId;
  const asset = assetId == undefined ? __new__ : assetId;
  return Trades(portfolio, asset);
};

const RouteEventList = (): JSX.Element => {
  const { portfolioId, assetId } = useParams<{
    portfolioId: string;
    assetId: string;
  }>();
  const portfolio = portfolioId == undefined ? __new__ : portfolioId;
  const asset = assetId == undefined ? __new__ : assetId;
  return Events(portfolio, asset);
};

const RouteTrnEdit = (): JSX.Element => {
  const { portfolioId, trnId } = useParams<{
    portfolioId: string;
    trnId: string;
  }>();
  const pId = portfolioId == undefined ? __new__ : portfolioId;
  const tId = trnId == undefined ? __new__ : trnId;
  return TransactionEdit(pId, tId);
};

const RoutePortfolioDelete = (): JSX.Element => {
  const { portfolioId } = useParams<{
    portfolioId: string;
  }>();
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
    path: "/trns/:portfolioId/asset/:assetId/trades",
    component: RouteTradeList,
  },
  {
    path: "/trns/:portfolioId/asset/:assetId/events",
    component: RouteEventList,
  },
  {
    path: "/trns/:portfolioId/:trnId",
    component: RouteTrnEdit,
  },
];

export default ClientRoutes;
