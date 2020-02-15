//import App from "./App";
import React from "react";
import { StaticRouter } from "react-router-dom";
import express from "express";
import { renderToString } from "react-dom/server";
import { Helmet } from "react-helmet";
import i18n from "./i18nConfig";
import Backend from "i18next-node-fs-backend";
import { I18nextProvider } from "react-i18next"; // has no proper import yet
import * as path from "path";
import fs from "fs";
import i18nextMiddleware from "i18next-express-middleware";
import logger from "./ConfigLogging";
import App from "./App";
import { runtimeConfig } from "./config";
import { holdings } from "./bcApi";

const appDirectory = fs.realpathSync(process.cwd());
const resolveApp = (relativePath: string): string => path.resolve(appDirectory, relativePath);

let assets: any;
let publicDir = "./";

if (process.env.RAZZLE_PUBLIC_DIR) {
  publicDir = process.env.RAZZLE_PUBLIC_DIR;
}

const syncLoadAssets = (): any => {
  logger.log("info", "Static Dir %s", `${resolveApp(publicDir)}`);
  if (process.env.RAZZLE_ASSETS_MANIFEST) {
    assets = require(process.env.RAZZLE_ASSETS_MANIFEST);
  }
};
syncLoadAssets();

const server = express();
let staticDir = "./";

if (process.env.RAZZLE_PUBLIC_DIR) {
  staticDir = process.env.RAZZLE_PUBLIC_DIR;
}
logger.info("bcConfig @ %s", JSON.stringify(runtimeConfig()));
i18n
  .use(Backend)
  .use(i18nextMiddleware.LanguageDetector)
  .init(
    {
      backend: {
        addPath: `${resolveApp(staticDir)}/locales/{{lng}}/{{ns}}.missing.json`,
        loadPath: `${resolveApp(staticDir)}/locales/{{lng}}/{{ns}}.json`
      },
      debug: false,
      defaultNS: "translations",
      nonExplicitWhitelist: true,
      ns: ["translations"],
      preload: ["en"]
    },
    () => {
      server
        .disable("x-powered-by")
        .use("/locales", express.static(`${resolveApp(staticDir)}/locales`))
        .use(i18nextMiddleware.handle(i18n))
        .use(express.urlencoded({ extended: true }))
        .use(express.static(staticDir))
        .use(express.json())
        .get("/bff/*/today", holdings)
        .get("/*", (req: express.Request, res: express.Response) => {
          logger.debug("Get %s", req.url);
          const context: any = {};
          const markup = renderToString(
            <I18nextProvider i18n={req.i18n}>
              <StaticRouter context={context} location={req.url}>
                <App />
              </StaticRouter>
            </I18nextProvider>
          );

          const helmet = Helmet.renderStatic();
          // This line must be placed after renderToString method
          // otherwise context won't be populated by App
          const { url } = context;

          if (url && req.url !== "/") {
            res.redirect(url);
          } else {
            const initialI18nStore = {};
            req.i18n.languages.forEach(l => {
              initialI18nStore[l] = req.i18n.services.resourceStore.data[l];
            });
            const initialLanguage = req.i18n.language;
            res.status(200).send(
              `<!doctype html>
    <html lang="en">
    <head>
        <title/>
        <meta http-equiv="X-UA-Compatible" content="IE=edge" />
        <meta charSet="utf-8" />
        ${helmet.title.toString()}
        ${helmet.meta.toString()}
        ${helmet.link.toString()}
        <meta name="viewport" content="initial-scale=1, maximum-scale=1">
        ${
          assets.client.css
            ? `<link rel="preload" as="style" href="${assets.client.css}">
              <link rel="stylesheet" href="${assets.client.css}">`
            : ""
        }
          ${
            process.env.NODE_ENV === "production"
              ? `<link rel="preload" as="script" href="${assets.client.js}">
                <script src="${assets.client.js}" defer></script>`
              : `<script src="${assets.client.js}" defer crossorigin></script>`
          }
          <script>
            window.initialLanguage = '${initialLanguage}';
            window.initialI18nStore = ${JSON.stringify(initialI18nStore)};
            window.env = ${JSON.stringify(runtimeConfig())};  
        </script>          
    </head>
    <style>
    html {
      font-family: sans-serif;
      -ms-text-size-adjust: 100%;
      -webkit-text-size-adjust: 100%;
    }
    body {
      margin: 0;
      -webkit-font-smoothing: antialiased;
      -moz-osx-font-smoothing: grayscale;
    }
    </style>
    <body>
        <div id="root">${markup}</div>
    </body>
</html>`
            );
          }
        });
    }
  );

export default server;
