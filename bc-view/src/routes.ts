import Home from './Home';
import ViewHoldings from './holdings';
import {useParams} from 'react-router-dom';

const RouteHoldings = (): JSX.Element => {
  const {portfolioId} = useParams();
  if (portfolioId) return ViewHoldings(portfolioId);
  return ViewHoldings('portfolioId');
};

const Routes = [
  {
    path: '/',
    exact: true,
    component: Home
  },
  {
    path: '/holdings/:portfolioId',
    component: RouteHoldings
  }
];

export default Routes;
