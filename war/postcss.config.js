/* eslint-env node */
module.exports = {
  parser: "postcss-less",
  plugins: [
    require('autoprefixer'),
    require('postcss-custom-properties')
  ]
};
