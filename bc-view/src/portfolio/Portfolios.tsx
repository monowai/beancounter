import React, { useEffect, useState } from "react";
import { Portfolio } from "../types/beancounter";
import { AxiosError } from "axios";
import logger from "../common/ConfigLogging";
import { axiosBff } from "../common/utils";
import { getBearerToken } from "../keycloak/utils";
import { useKeycloak } from "@react-keycloak/web";
import { Link } from "react-router-dom";

export default function Portfolios(): React.ReactElement {
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
      await axiosBff()
        .get<Portfolio[]>("/bff/portfolios", config)
        .then(result => {
          logger.debug("<<fetched apiPortfolios");
          setPortfolios(result.data);
        })
        .catch(err => {
          setError(err);
          if (err.response) {
            logger.error("bff error [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
    };
    fetchPortfolios({
      headers: getBearerToken(keycloak)
    }).finally(() => setLoading(false));
  }, [keycloak]);
  // Render where we are in the initialization process
  if (loading) {
    return <div id="root">Loading...</div>;
  }
  if (error) {
    const { response } = error;
    if (response) {
      const { data: errData, status } = response;
      if (status === 401) {
        return <div>Please log in</div>;
      }
      logger.error("Error: %s", errData.message);
      return <div>{errData.message}</div>;
    }
    return <div>Unknown error</div>;
  }
  if (portfolios) {
    return (
      <div className="page-box">
        <table className={"table is-striped is-hoverable"}>
          <tbody>
            {portfolios.map(portfolio => (
              <tr key={portfolio.id}>
                <td align={"left"}>
                  <Link to={`/holdings/${portfolio.code}`}>{portfolio.code}</Link>
                </td>
                <td align={"left"}>{portfolio.name}</td>
                <td>
                  {portfolio.currency.symbol}
                  {portfolio.currency.code}
                </td>
                <td>
                  {portfolio.base.symbol}
                  {portfolio.base.code}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  }
  return <div id="root">You have no portfolios - create one?</div>;
}
