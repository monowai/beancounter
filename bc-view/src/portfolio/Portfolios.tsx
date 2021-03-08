import React from "react";
import { Link } from "react-router-dom";
import { useHistory } from "react-router";
import { usePortfolios } from "./hooks";
import { isDone } from "../types/typeUtils";
import { ShowError } from "../errors/ShowError";

export function Portfolios(): React.ReactElement {
  const portfolioResults = usePortfolios();
  const history = useHistory();
  if (!isDone(portfolioResults)) {
    return (
      <div id="root" data-testid="loading">
        Loading...
      </div>
    );
  }
  if (portfolioResults.error) {
    return <ShowError error={portfolioResults.error} />;
  }
  const portfolios = portfolioResults.data;
  if (portfolios.length > 0) {
    return (
      <div>
        <nav className="container has-background-grey-lighter">
          <div className="column is-left">
            <button
              className="navbar-item button is-link is-small"
              onClick={() => {
                return history.push("/portfolios/new");
              }}
            >
              Create
            </button>
          </div>
        </nav>
        <div className="page-box is-primary has-background-light">
          <div className="container">
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
                {portfolios.map((portfolio) => (
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
                      <Link className="far fa-edit" to={`/portfolios/${portfolio.id}`} />
                      <span> </span>
                      <Link
                        className="far fa-trash-alt has-padding-left-6"
                        to={`/portfolios/${portfolio.id}/delete`}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    );
  }
  return (
    <div id="root">
      You have no portfolios - <Link to={"/portfolios/new"}>create one?</Link>
    </div>
  );
}

export default Portfolios;
