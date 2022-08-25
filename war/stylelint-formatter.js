/* eslint-env node */
// TODO remove this file once https://github.com/stylelint/stylelint/issues/6100 is released
const reporter = require("stylelint-checkstyle-reporter");

/**
 * @type {import("stylelint").Formatter}
 */
function formatter(results, returnValue) {
  returnValue.output = reporter(results);

  return returnValue.output;
}

module.exports = formatter;
