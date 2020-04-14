import React, { useCallback } from "react";
import { useDropzone } from "react-dropzone";
import { Portfolio } from "../types/beancounter";
import { useKeycloak } from "@react-keycloak/razzle";
import logger from "../common/configLogging";
import { _axios, getBearerToken } from "../common/axiosUtils";

export function TrnDropZone(props: { portfolio: Portfolio }): React.ReactElement {
  const [keycloak] = useKeycloak();
  // https://github.com/react-dropzone/react-dropzone
  const onDrop = useCallback(
    (acceptedFiles) => {
      acceptedFiles.forEach((file) => {
        const reader = new FileReader();
        reader.onabort = () => logger.debug("file reading was aborted");
        reader.onerror = () => logger.debug("file reading has failed");
        reader.onload = () => {
          // Do whatever you want with the file contents
          if (typeof reader.result === "string") {
            const results = reader.result.split("\r\n");
            let headerSkipped = false;
            let rows = 0;
            results.forEach(function (value) {
              if (headerSkipped) {
                rows++;
                const row = value.match(/(".*?"|[^",\s]+)(?=\s*,|\s*$)/g);
                _axios
                  .post<string>(
                    "/upload/trn",
                    { portfolio: props.portfolio, row },
                    {
                      headers: getBearerToken(keycloak.token),
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
              } else {
                headerSkipped = true;
              }
            });
            logger.debug("<<POST trnUpload sent %s", rows);
          }
        };
        reader.readAsText(file, "utf-8");
      });
    },
    [props.portfolio, keycloak]
  );
  const { getRootProps, getInputProps } = useDropzone({ onDrop });

  if (props.portfolio.id === "new") {
    return <div />;
  }
  return (
    <div {...getRootProps()}>
      <input {...getInputProps()} />
      <span>
        <i className="far fa-arrow-alt-circle-up fa-3x"></i>
      </span>
    </div>
  );
}
