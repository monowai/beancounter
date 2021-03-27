import { _axios, getBearerToken } from "../common/axiosUtils";
import { DelimitedImport } from "../types/app";

export function writeRows(params: DelimitedImport): number {
  let headerSkipped = false;
  let rows = 0;
  let purged = false;
  params.results.forEach(function (row) {
    if (!params.hasHeader || headerSkipped) {
      if (row && row.length >1 && !row[0].startsWith("#")) {
        rows++;
        _axios
          .post<string>(
            "/upload/trn",
            { portfolio: params.portfolio, row },
            {
              headers: getBearerToken(params.token),
            }
          )
          .catch((err) => {
            if (err.response) {
              console.error(
                "axios error [%s]: [%s]",
                err.response.status,
                err.response.data.message
              );
            }
          });
      }
    } else {
      // Something to process so we will purge the existing positions
      // Currently not tracking primary keys so this prevents duplicate trns
      if (!purged && params.purge) {
        _axios
          .delete(`/bff/trns/portfolio/${params.portfolio.id}`, {
            headers: getBearerToken(params.token),
          })
          .then(() => (purged = true))
          .catch((err) => {
            if (err.response) {
              console.error(
                "axios error [%s]: [%s]",
                err.response.status,
                err.response.data.message
              );
            }
          });
      }
      headerSkipped = true;
      _axios
        .post<string>(
          "/upload/trn",
          { portfolio: params.portfolio, message: "Starting Import" },
          {
            headers: getBearerToken(params.token),
          }
        )
        .catch((err) => {
          if (err.response) {
            console.error("axios error [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
    }
  });
  return rows;
}
