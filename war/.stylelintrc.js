module.exports = {
  extends: "stylelint-config-standard",
  customSyntax: "postcss-less",
  rules: {
    "indentation": null,
    "linebreaks": "unix",
    "max-line-length": 150,
    "selector-list-comma-newline-after": null,
    "selector-list-comma-space-after": null,
    "value-list-comma-newline-after": null,
    "declaration-colon-newline-after": null,
    "no-descending-specificity": null,
    "selector-class-pattern": "[a-z]",
    "selector-id-pattern": "[a-z]",
    "custom-property-pattern": "[a-z]",
    "function-name-case": "lower",
    "value-keyword-case": [
      "lower",
      {
        "camelCaseSvgKeywords": true
      }
    ],
    "string-quotes": "double",
    "property-no-vendor-prefix": null,
    "at-rule-no-vendor-prefix": null,
    "color-function-notation": "legacy",
    "alpha-value-notation": "number",
    "number-max-precision": 5,
    "function-no-unknown": null,
    "selector-type-no-unknown": null,
    "font-family-no-missing-generic-family-keyword": null,
    "declaration-block-single-line-max-declarations": 2,
    "function-url-quotes": "always",
    "no-duplicate-selectors": null,
    "no-invalid-position-at-import-rule": null,
    "hue-degree-notation": "number",
  }
}
