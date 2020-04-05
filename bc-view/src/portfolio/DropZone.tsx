import React, { useCallback } from "react";
import { useDropzone } from "react-dropzone";
import { Portfolio } from "../types/beancounter";

export function TrnDropZone(props: { portfolio: Portfolio }): React.ReactElement {
  // https://github.com/react-dropzone/react-dropzone
  const onDrop = useCallback(acceptedFiles => {
    acceptedFiles.forEach(file => {
      const reader = new FileReader();

      reader.onabort = () => console.log("file reading was aborted");
      reader.onerror = () => console.log("file reading has failed");
      reader.onload = () => {
        // Do whatever you want with the file contents
        const result = reader.result;
        console.log(props.portfolio, result);
      };

      reader.readAsText(file, "utf-8");
    });
  }, []);
  const { getRootProps, getInputProps } = useDropzone({ onDrop });

  return (
    <div {...getRootProps()}>
      <input {...getInputProps()} />
      <p>Drag drop some files here, or click to select files</p>
    </div>
  );
}
