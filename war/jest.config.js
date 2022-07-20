module.exports = {
  transform: {
    "\\.hbs$": "jest-handlebars",
    "\\.js$": "babel-jest"
  },
  reporters: [
    "jest-standard-reporter",
    [
      "jest-junit",
      {
        outputName: "jest-result.xml",
        outputDirectory: "target"
      }
    ]
  ],
  roots: ["<rootDir>/src"]
}
