import React from "react";
import "../assets/styles.sass";
import { Portfolio } from "../types/beancounter";
import { translate } from "../ConfigI18n";

export default function PortfolioSummary(portfolio: Portfolio): JSX.Element {
  return (
        <tbody className={"table-container is-large"} key={portfolio.code}>
          <tr>
            <th>SUMMARY</th>
            <th colSpan={5}> </th>
            <th align={"right"}>{translate("dividends")}</th>
            <th align={"right"}>{translate("gain.realised")}</th>
            <th align={"right"}>{translate("gain.unrealised")}</th>
            <th align={"right"}>{translate("gain")}</th>
          </tr>
        </tbody>
  );
}
