module.exports = {
    parser: "@typescript-eslint/parser",  // Specifies the ESLint parser
    "extends": [
        "eslint:recommended",
        "plugin:react/recommended",
        "plugin:@typescript-eslint/recommended",
        "prettier/@typescript-eslint",
        "plugin:prettier/recommended"
    ],
    "plugins": ["@typescript-eslint"],
    settings: {
        "react": {
            "version": "detect"
        }
    },

    parserOptions: {
        ecmaVersion: 2018,  // Allows for the parsing of modern ECMAScript features
        sourceType: "module"  // Allows for the use of imports
    },

    rules: {
        "strict": "error",
        "@typescript-eslint/explicit-member-accessibility": [1, { accessibility: 'no-public' }],
        "complexity": ["error", 5],
        "max-nested-callbacks": ["error", 3],
        "max-params": ["error", 4],
        "max-depth": ["error", 3],
        "react/jsx-sort-default-props": ["error", { "ignoreCase": false }],
        "max-len": [
            "error",
            {
                "code": 80,
                "ignoreStrings": true,
                "ignoreComments": true,
                "ignoreTemplateLiterals": true
            }
        ],
        "require-await": "error",
        "no-func-assign": "error",
        "object-shorthand": [
            "error",
            "methods",
            { "avoidExplicitReturnArrows": false }
        ],
        "object-curly-spacing": ["error", "always"],
        "prefer-const": ["error", {
            "destructuring": "any",
            "ignoreReadBeforeAssign": false
        }],
        "no-useless-return": "error",
        "no-else-return": "error",
        "no-return-await": "error",
        "no-var": "error",
        '@typescript-eslint/explicit-function-return-type': ['warn', {
            allowExpressions: true,
            allowTypedFunctionExpressions: true
        }],
        "@typescript-eslint/no-explicit-any": "off",
    },
    env: {
        "es6": true,
        "browser": true,
        "node": true,
        "jest": true
    }
};