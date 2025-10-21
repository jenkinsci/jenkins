module.exports = {
  extends: "stylelint-config-standard-scss",
  ignoreFiles: ["src/main/scss/_bootstrap.scss"],
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
    "alpha-value-notation": "number",
    "number-max-precision": 5,
    "no-duplicate-selectors": null,
    "hue-degree-notation": "number",
  },
};
