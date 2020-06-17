import { _axios, getBearerToken } from "../common/axiosUtils";
import logger from "../common/configLogging";
import { DelimitedImport } from "../types/app";

export function writeRows(params: DelimitedImport): number {
  let headerSkipped = false;
  let rows = 0;
  params.results.forEach(function (value) {
    if (!params.hasHeader || headerSkipped) {
      rows++;
      const row = value.match(/(".*?"|[^",\s]+)(?=\s*,|\s*$)/g);
      if (row && !row[0].startsWith("#")) {
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
              logger.error(
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
      _axios
        .delete(`/bff/trns/portfolio/${params.portfolio.id}`, {
          headers: getBearerToken(params.token),
        })
        .catch((err) => {
          if (err.response) {
            logger.error("axios error [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
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
            logger.error("axios error [%s]: [%s]", err.response.status, err.response.data.message);
          }
        });
    }
  });
  return rows;
}
