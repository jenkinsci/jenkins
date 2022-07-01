module.exports = {
  extends: "stylelint-config-standard",
  rules: {
    indentation: null,
    "selector-list-comma-newline-after": "never-multi-line",
    "selector-list-comma-space-after": "always"
  },
  // Keeps the default level to warn to avoid breaking the current
  // CI build environment
  defaultSeverity: "warning"
}
