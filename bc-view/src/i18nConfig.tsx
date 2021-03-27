import i18n, { InitOptions } from "i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import { initReactI18next } from "react-i18next";
import Backend from "i18next-http-backend";

const options: InitOptions = {
  debug: false,
  defaultNS: "translations",
  fallbackLng: "en",
  nonExplicitSupportedLngs: true,
  whitelist: ["en"],
  interpolation: {
    escapeValue: false, // react already safes from xss
    formatSeparator: ",",
    format: (value, format, lng) => {
      if (!format || !lng || !value) {
        return value;
      }
      return value;
    },
  },
  keySeparator: false, // we do not use keys in form messages.welcome
  lng: "en",
  load: "languageOnly",
  ns: ["translations"],
  backend: {
    // for all available options read the backend's repository readme file
    loadPath: "/locales/{{lng}}/{{ns}}.json",
  },
  react: {
    transKeepBasicHtmlNodesFor: ["br", "strong", "i", "p", "\n"],
    transSupportBasicHtmlNodes: true,
  },
};

if (process && !process.release) {
  i18n.use(Backend).use(LanguageDetector).use(initReactI18next);
}

// initialize if not already initialized
if (!i18n.isInitialized) {
  i18n.init(options).then((r) => {
    console.info("i18n " + r("app") + " initialised");
  });
}

export default i18n;
