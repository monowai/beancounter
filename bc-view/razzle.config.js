/* eslint-disable */
const { findIndex, has, remove } = require("ramda");
const modifyBuilder = require("razzle-plugin-postcss").default;
const cssConfig = {
  postcssPlugins: [
    require("postcss-flexbugs-fixes"),
    require("autoprefixer")
  ]
};

const modify = modifyBuilder({ cssConfig });

module.exports = {
  plugins: [
    "scss",
    { func: modify },
    {
      name: "typescript",
      options: {
        useBabel: true,
        useEslint: true,
        forkTsChecker: {
          tsconfig: "./tsconfig.json",
          tslint: false, // All linting is via ESLINT
          watch: "./src",
          typeCheck: true
        }
      }
    }
  ],
  modify(config, { target, dev: IS_DEV }, webpackObject) {

    const { module } = config;
    const { rules } = module;
    const fileLoaderIdx = findIndex(item => {
      return has("exclude", item) && item.exclude.length > 10;
    }, rules);
    const fileLoader = rules[fileLoaderIdx];
    fileLoader.exclude = [...fileLoader.exclude, /\.woff$/, /\.woff2$/];
    const newRule = {
      include: [/\.woff$/, /\.woff2$/],
      loader: require.resolve("file-loader"),
      options: {
        name: "static/media/[name].[ext]",
        emitFile: target === "web"
      }
    };

    return {
      ...config,
      module: {
        ...module,
        rules: [...remove(fileLoaderIdx, 1, rules), fileLoader, newRule]
      }
    };
  }
};
