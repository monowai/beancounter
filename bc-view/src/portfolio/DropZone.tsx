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

        reader.onabort = () => console.log("file reading was aborted");
        reader.onerror = () => console.log("file reading has failed");
        reader.onload = () => {
          // Do whatever you want with the file contents
          if (typeof reader.result === "string") {
            const results = reader.result.split("\r\n");
            let headerSkipped = false;
            results.forEach(function (value) {
              if (headerSkipped) {
                const row = value.match(/(".*?"|[^",\s]+)(?=\s*,|\s*$)/g);
                // for (const rowElement of row) {
                //   rowElement.replace(/\"/g, "");
                // }
                _axios
                  .post<string>(
                    "/upload/trn",
                    { portfolio: props.portfolio, row },
                    {
                      headers: getBearerToken(keycloak.token),
                    }
                  )
                  .then((result) => {
                    logger.debug("<<POST trnUpload %s", result.data);
                  })
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
          }
          // console.log(props.portfolio, result);
        };

        reader.readAsText(file, "utf-8");
      });
    },
    [props.portfolio, keycloak]
  );
  const { getRootProps, getInputProps } = useDropzone({ onDrop });

  return (
    <div {...getRootProps()}>
      <input {...getInputProps()} />
      <p>Drag drop some files here, or click to select files</p>
    </div>
  );
}
