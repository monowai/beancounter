import React from "react";
import { Link } from "react-router-dom";
import { withRouter } from "react-router";
import { usePortfolios } from "./hooks";
import handleError from "../common/errors/UserError";

export function Portfolios(): React.ReactElement {
  const [portfolios, error] = usePortfolios();

  // Render where we are in the initialization process
  if (!portfolios) {
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
                            <Link className="fa fa-edit" to={`/portfolio/${portfolio.id}`} />
                            <span> </span>
                            <Link
                              className="fa fa-remove has-padding-left-6"
                              to={`/portfolio/delete/${portfolio.id}`}
                            />
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
  return (
    <div id="root">
      You have no portfolios - <Link to={"/portfolio/new"}>create one?</Link>
    </div>
  );
}

export default withRouter(Portfolios);
