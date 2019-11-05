import express from "express";
import React from "react";
import { renderToString } from "react-dom/server";
import { StaticRouter } from "react-router";
import fs from "fs";

import App from "./App";
// import i18nextMiddleware from "i18next-express-middleware";
import * as path from "path";

const appDirectory = fs.realpathSync(process.cwd());
const resolveApp = (relativePath: string): string =>
  path.resolve(appDirectory, relativePath);

let assets: any;
let publicDir = "./";

if (process.env.RAZZLE_PUBLIC_DIR) {
  publicDir = process.env.RAZZLE_PUBLIC_DIR;
}

const syncLoadAssets = (): any => {
  console.log("Static Dir" + `${resolveApp(publicDir)}`);
  if (process.env.RAZZLE_ASSETS_MANIFEST) {
    assets = require(process.env.RAZZLE_ASSETS_MANIFEST);
  }
};
syncLoadAssets();

const server = express()
  .disable("x-powered-by")
  .use(express.static(publicDir))
  .get("/*", (req: express.Request, res: express.Response) => {
    const context = {};
    const markup = renderToString(
      <StaticRouter context={context} location={req.url}>
        <App />
      </StaticRouter>
    );
    // FixMe

    res.send(
      `<!doctype html>
    <html lang="">
    <head>
        <meta http-equiv="X-UA-Compatible" content="IE=edge" />
        <meta charSet='utf-8' />
        <title>Beancounter</title>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        ${
          assets.client.css
            ? `<link rel="stylesheet" href="${assets.client.css}">`
            : ""
        }
          ${
            process.env.NODE_ENV === "production"
              ? `<script src="${assets.client.js}" defer></script>`
              : `<script src="${assets.client.js}" defer crossorigin></script>`
          }
    </head>
    <body>
        <div id="root">${markup}</div>
    </body>
</html>`
    );
  });

export default server;
