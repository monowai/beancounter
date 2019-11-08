import i18n from "i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import { initReactI18next } from "react-i18next";
import XHR from "i18next-xhr-backend";
import logger from "./ConfigLogging";

if (process && !process.release) {
  i18n
    .use(XHR)
    .use(LanguageDetector)
    .use(initReactI18next);
}

// initialize if not already initialized
if (!i18n.isInitialized) {
  i18n.init(
    {
      debug: false,
      defaultNS: "translations",
      fallbackLng: "en",
      nonExplicitWhitelist: true,
      whitelist: ["en"],
      interpolation: {
        escapeValue: false, // react already safes from xss
        formatSeparator: ",",
        format: (value, format, lng) => {
          if (!format || !lng || !value) {
            return value;
          }
          return value;
        }
      },
      keySeparator: false, // we do not use keys in form messages.welcome
      lng: "en",
      load: "languageOnly",
      ns: ["translations"],
      backend: {
        // for all available options read the backend's repository readme file
        loadPath: "/locales/{{lng}}/{{ns}}.json"
      },
      react: {
        transKeepBasicHtmlNodesFor: ["br", "strong", "i", "p", "\n"],
        transSupportBasicHtmlNodes: true
      }
    },
    function(err, t) {
      // initialized and ready to go!
      logger.info("i18n " + t("app") + " initialised");
    }
  );
}

export function translate(key: string): string {
  return i18n.t(key);
}

export default i18n;
