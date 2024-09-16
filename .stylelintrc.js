module.exports = {
  extends: "stylelint-config-standard",
  customSyntax: "postcss-scss",
  ignoreFiles: ["war/src/main/scss/_bootstrap.scss"],
  rules: {
    "no-descending-specificity": null,
    "selector-class-pattern": "[a-z]",
    "selector-id-pattern": "[a-z]",
    "custom-property-pattern": "[a-z]",
    "value-keyword-case": [
      "lower",
      {
        camelCaseSvgKeywords: true,
      },
    ],
    "property-no-vendor-prefix": null,
    "at-rule-no-unknown": [
      true,
      {
        ignoreAtRules: [
          "function",
          "if",
          "each",
          "include",
          "mixin",
          "for",
          "use",
        ],
      },
    ],
    "color-function-notation": "legacy",
    "alpha-value-notation": "number",
    "number-max-precision": 5,
    "function-no-unknown": null,
    "no-duplicate-selectors": null,
    "hue-degree-notation": "number",
  },
};
