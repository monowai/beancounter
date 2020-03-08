import React, { useEffect, useState } from "react";
import { Portfolio } from "../types/beancounter";
import { AxiosError } from "axios";
import logger from "../common/ConfigLogging";
import { Link } from "react-router-dom";
import handleError from "../common/errors/UserError";
import { _axios, getBearerToken, setToken } from "../common/axiosUtils";
import { useKeycloak } from "@react-keycloak/web";
import { withRouter } from "react-router";

export function Portfolios(): React.ReactElement {
  const [portfolios, setPortfolios] = useState<Portfolio[]>();
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<AxiosError>();
  const [keycloak] = useKeycloak();

  useEffect(() => {
    const fetchPortfolios = async (config: {
      headers: { Authorization: string };
    }): Promise<void> => {
      setLoading(true);
      logger.debug(">>fetch apiPortfolios");
      await _axios
        .get<Portfolio[]>("/bff/portfolios", config)
        .then(result => {
          logger.debug("<<fetched apiPortfolios");
          setPortfolios(result.data);
        })
        .catch(err => {
          setError(err);
          if (err.response) {
            logger.error("axios error [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
    };
    setToken(keycloak);
    fetchPortfolios({
      headers: getBearerToken()
    }).finally(() => setLoading(false));
  }, [keycloak]);
  // Render where we are in the initialization process
  if (loading) {
    return <div id="root">Loading...</div>;
  }
  if (error) {
    return handleError(error, true);
  }
  if (portfolios) {
    if (portfolios.length > 0) {
      return (
        <div>
          <section className="page-box hero is-primary is-fullheight has-background-light">
            <div className="hero-body">
              <div className="container">
                <div className="columns is-centered">
                  <table className={"table is-striped is-hoverable"}>
                    <thead>
                      <tr>
                        <th>Code</th>
                        <th>Name</th>
                        <th>Report</th>
                        <th>Base</th>
                        <th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {portfolios.map(portfolio => (
                        <tr key={portfolio.id}>
                          <td>
                            <Link to={`/holdings/${portfolio.code}`}>{portfolio.code}</Link>
                          </td>
                          <td>{portfolio.name}</td>
                          <td>
                            {portfolio.currency.symbol}
                            {portfolio.currency.code}
                          </td>
                          <td>
                            {portfolio.base.symbol}
                            {portfolio.base.code}
                          </td>
                          <td>
                            <Link className="fa fa-edit" to={`/portfolio/${portfolio.code}`} />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </section>
        </div>
      );
    }
  }
  return <div id="root">You have no portfolios - create one?</div>;
}

export default withRouter(Portfolios);
