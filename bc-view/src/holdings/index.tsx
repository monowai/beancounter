import { HoldingFooter, HoldingHeader, HoldingRows } from "./Group";
import React, { useEffect, useState } from "react";
import "../App.css";
import { calculate } from "./calculate";
import { GroupBy, groupOptions } from "../types/groupBy";
import { GroupOption, Holdings, ValuationOption } from "../types/beancounter";
import useAxios from "axios-hooks";
import Total from "./Total";
import StatsHeader, { StatsRow } from "../portfolio/Stats";
import Switch from "react-switch";
import Select, { ValueType } from "react-select";
import { valuationOptions, ValueIn } from "../types/valueBy";
import { runtimeConfig } from "../config";

export default function ViewHoldings(portfolioId: string | undefined): JSX.Element {
  const [getPortfolio] = useState(portfolioId);

  const [{ data: getData, loading: getLoading, error: getError }, getHoldings] = useAxios(
    {
      url: runtimeConfig().bcService + "/" + portfolioId + "/today",
      method: "GET"
    },
    { manual: true }
  );

  useEffect(() => {
    getHoldings();
  }, [getPortfolio]);
  const [valueIn, setValueIn] = useState<ValuationOption>({
    value: ValueIn.PORTFOLIO,
    label: "Portfolio"
  });
  const [hideEmpty, setHideEmpty] = useState<boolean>(true);
  const [groupBy, setGroupBy] = useState<GroupOption>({
    value: GroupBy.MARKET_CURRENCY,
    label: "Currency"
  });

  if (getLoading) {
    return <div id="root">Loading...</div>;
  }
  if (getError) {
    return <div id="root">${getError.message}</div>;
  }
  if (getData) {
    const holdings = calculate(getData.data, hideEmpty, valueIn.value, groupBy.value) as Holdings;

    return (
      <div className="page-box">
        <div className="filter-columns">
          <div className="filter-label" />
          <div className="filter-label" />
          <div className="filter-label">Value In</div>
          <div className="filter-column">
            <Select
              options={valuationOptions()}
              defaultValue={valueIn}
              isSearchable={false}
              isClearable={false}
              onChange={(newValue: ValueType<ValuationOption>) => {
                if (newValue) {
                  setValueIn(newValue as ValuationOption);
                }
              }}
            />
          </div>
          <div className="filter-label">Group By</div>
          <div className="filter-column">
            <Select
              options={groupOptions()}
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
          <div className="filter-label">Open Only</div>
          <div className="filter-column">
            <Switch className="react-switch" onChange={setHideEmpty} checked={hideEmpty} required />
          </div>
        </div>
        <div className={"stats-container"}>
          <table>
            <StatsHeader portfolio={getData.data.portfolio} />
            <StatsRow
              portfolio={getData.data.portfolio}
              moneyValues={holdings.totals}
              valueIn={valueIn.value}
            />
          </table>
        </div>
        <div className={"all-holdings"}>
          <table className={"table is-striped is-hoverable"}>
            {Object.keys(holdings.holdingGroups)
              .sort()
              .map(groupKey => {
                return (
                  <React.Fragment key={groupKey}>
                    <HoldingHeader groupKey={groupKey} />
                    <HoldingRows
                      holdingGroup={holdings.holdingGroups[groupKey]}
                      valueIn={valueIn.value}
                    />
                    <HoldingFooter
                      holdingGroup={holdings.holdingGroups[groupKey]}
                      valueIn={valueIn.value}
                    />
                  </React.Fragment>
                );
              })}
            <Total holdings={holdings} valueIn={valueIn.value} />
          </table>
        </div>
      </div>
    );
  }
  return <div id="root">No Holdings to display</div>;
}
