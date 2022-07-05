module.exports = {
  extends: "stylelint-config-standard",
  customSyntax: "postcss-less",
  rules: {
    "indentation": null,
    "linebreaks": "unix",
    "max-line-length": 150,
    "selector-list-comma-newline-after": "never-multi-line",
    "selector-list-comma-space-after": "always"
  },
  // Keeps the default level to warn to avoid breaking the current
  // CI build environment
  defaultSeverity: "warning"
}
