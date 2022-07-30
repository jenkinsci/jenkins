/* eslint-env node */
const reporter = require('stylelint-checkstyle-reporter')

/**
 * @type {import('stylelint').Formatter}
 */
function formatter(results, returnValue) {
  returnValue.output = reporter(results)

  return returnValue.output
}

module.exports = formatter;
