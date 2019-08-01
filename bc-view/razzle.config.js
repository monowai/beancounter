"use strict";

module.exports = {
    plugins: [{
        name: "typescript",
        options: {
            useBabel: true,
            useEslint: true,
            forkTsChecker: {
                tsconfig: "./tsconfig.json",
                tslint: false, // All linting is via ESLINT
                watch: "./src",
                typeCheck: true,
            },
        },
    },],
};