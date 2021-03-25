import express from "express";
import "./../node_modules/bulma/css/bulma.css";
import logger from "./server/nodeLogging";

// this require is necessary for server HMR to recover from error
// eslint-disable-next-line @typescript-eslint/no-var-requires
let server = require("./server").default;

if (module.hot) {
  module.hot.accept("./server", () => {
    console.info("🔁  HMR Reloading `./server`...");
    try {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      server = require("./server").default;
    } catch (error) {
      logger.error(error);
    }
  });
  logger.info("✅  Server-side HMR is Enabled!");
}

const port = process.env.PORT || 3000;

export default express()
  .use((req, res) => server.handle(req, res))
  .listen(port, () => {
    logger.info("> Started on port %s", port);
  })
  .on("error", (e) => {
    logger.error(e.message);
    throw e;
  });
