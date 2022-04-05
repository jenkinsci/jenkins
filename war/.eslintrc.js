/* eslint-env node */
module.exports = {
    env: {
        browser: true,
        es6: true
    },
    // Uses eslint default ruleset
    extends: "eslint:recommended",
    plugins: [
        // Keeps the default level to warn to avoid breaking the current
        // CI build environment
        "only-warn"
    ],
    parserOptions: {
        ecmaVersion: 2018,
        sourceType: "module"
    },
    rules: {
    },
    globals: {
        Atomics: "readonly",
        SharedArrayBuffer: "readonly",

        '__dirname': false,

        // Allow jest globals used in tests
        jest: false,
        expect: false,
        it: false,
        describe: false,
        beforeEach: false,
        afterEach: false,
        beforeAll: false,
        afterAll: false,
    }
};
