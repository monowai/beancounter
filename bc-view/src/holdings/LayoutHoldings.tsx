import { HoldingFooter, HoldingHeader, HoldingRows } from "./GroupedHoldings";
import React, { useState } from "react";
import "../App.css";
import { computeHoldings } from "./computeHoldings";
import { description, GroupBy, GroupOptions } from "./groupBy";
import { CurrencyOption, GroupOption, Holdings } from "../types/beancounter";
import useAxios from "axios-hooks";
import Totals from "./Totals";
import StatsHeader, { StatsRow } from "../portfolio/PortfolioStats";
import Switch from "react-switch";
import Select, { ValueType } from "react-select";
import { CurrencyOptions, CurrencyValues } from "./valueBy";

const LayoutHoldings = (): JSX.Element => {
  const [valueBy, setValueBy] = useState<CurrencyOption>({
    value: CurrencyValues.PORTFOLIO,
    label: "Portfolio"
  });
  // const [portfolioValueIn, setPortfolioValueIn] = useState<ValuationCcy>("BASE");
  const [hideEmpty, setHideEmpty] = useState<boolean>(true);
  const [groupBy, setGroupBy] = useState<GroupOption>({
    value: GroupBy.MARKET_CURRENCY,
    label: "Currency"
  });
  const [axiosResponse] = useAxios("http://localhost:9500/api/" + "test");
  if (axiosResponse.loading) {
    return <p>Loading...</p>;
  }
  if (axiosResponse.error) {
    return <p>{axiosResponse.error.message}!</p>;
  }
  if (axiosResponse.data) {
    const holdings = computeHoldings(
      axiosResponse.data.data,
      hideEmpty,
      valueBy.value,
      groupBy.value
    ) as Holdings;

    return (
      <div className={"page-box"}>
        <div className="filter">
          <div />
          <div />
          <div className={"filter-column"}>
            <div>Value In</div>
            <Select
              options={CurrencyOptions}
              defaultValue={valueBy}
              isSearchable={false}
              isClearable={false}
              onChange={(newValue: ValueType<CurrencyOption>) => {
                if (newValue) {
                  setValueBy(newValue as CurrencyOption);
                }
              }}
            />
          </div>
          <div className={"filter-column"}>
            <div>Group By</div>
            <Select
              options={GroupOptions}
              defaultValue={groupBy}
              isSearchable={false}
              isClearable={false}
              onChange={(newValue: ValueType<GroupOption>) => {
                if (newValue) {
                  setGroupBy(newValue as GroupOption);
                }
              }}
            />
          </div>
          <div>
            <div>Hide Closed</div>
            <Switch
              className="react-switch"
              onChange={setHideEmpty}
              checked={hideEmpty}
              required
            />
          </div>
        </div>
        <div className={"stats-container"}>
          <table>
            <StatsHeader portfolio={axiosResponse.data.data.portfolio} />
            <StatsRow
              portfolio={axiosResponse.data.data.portfolio}
              moneyValues={holdings.totals}
              valueIn={valueBy.value}
            />
          </table>
        </div>
        <div className={"group-header"}>
          Grouped by {description(groupBy.value)}
        </div>

        <div>
          <table className={"table is-striped is-hoverable"}>
            {Object.keys(holdings.holdingGroups).map(groupKey => {
              return (
                <React.Fragment key={groupKey}>
                  <HoldingHeader groupKey={groupKey} />
                  <HoldingRows
                    holdingGroup={holdings.holdingGroups[groupKey]}
                    valueIn={valueBy.value}
                  />
                  <HoldingFooter
                    holdingGroup={holdings.holdingGroups[groupKey]}
                    valueIn={valueBy.value}
                  />
                </React.Fragment>
              );
            })}
            <Totals holdings={holdings} valueIn={valueBy.value} />
          </table>
        </div>
      </div>
    );
  }
  return <div>No Holdings...</div>;
};

export default LayoutHoldings;
