/* eslint-env node */
module.exports = {
    env: {
        browser: true,
        es6: true
    },
    // Uses eslint default ruleset
    extends: "eslint:recommended",
    parserOptions: {
        ecmaVersion: 2018,
        sourceType: "module"
    },
    rules: {
    },
    globals: {
        $: "readonly",
        Ajax: "readonly",
        Atomics: "readonly",
        Behaviour: "readonly",
        getElementOverflowParams: "readonly",
        global: "readonly",
        Hash: "readonly",
        isPageVisible: "readonly",
        isRunAsTest: "readonly",
        layoutUpdateCallback: "readonly",
        onSetupWizardInitialized: "readonly",
        setupWizardExtensions: "readonly",
        SharedArrayBuffer: "readonly",
        toQueryString: "readonly",

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
