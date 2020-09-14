import express from "express";
import logger from "./common/configLogging";
import "./../node_modules/bulma/css/bulma.css";

// this require is necessary for server HMR to recover from error
// eslint-disable-next-line @typescript-eslint/no-var-requires
let server = require("./server").default;

if (module.hot) {
  module.hot.accept("./server", () => {
    logger.info("🔁  HMR Reloading `./server`...");
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
