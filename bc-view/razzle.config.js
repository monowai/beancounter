/* eslint-disable */
const { findIndex, has, remove } = require("ramda");

module.exports = {
  plugins: [
    {
      name: "scss",
      options: {
        dev: {
          sourceMap: true,
          ident: 'postcss',
          exportOnlyLocals: true,
        },
        prod: {
          sourceMap: false,
          ident: 'postcss',
        },
        plugins: [
          require("postcss-flexbugs-fixes"),
          require("autoprefixer")({
            // overrideBrowserslist: ['>1%', 'last 4 versions', 'Firefox ESR', 'not ie < 9'],
            flexbox: 'no-2009',
          }),
        ],
      }
    },
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
  modify(config, { target, dev: IS_DEV }, webpack) {

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
      node: { fs: "empty"},
      module: {
        ...module,
        rules: [...remove(fileLoaderIdx, 1, rules), fileLoader, newRule]
      }
    };
  }
};
