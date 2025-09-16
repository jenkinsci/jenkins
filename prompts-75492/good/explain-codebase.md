### Model

Gemini 2.5 Pro w/Github Copilot

### Prompt

Can you provide a list of all the technologies used in this repo? Create a breakdown by programming language. (For example: Java, JavaScript etc.)

Write the details in the explain-codebase.md file under the Response heading.

- Provided pom.xml & package.json as context

### Response

Based on the project's configuration files, here is a breakdown of the technologies used, categorized by programming language.

### Java

*   **Build Tool**: Maven is used for building the Java parts of the project, managing dependencies, and running tasks.
*   **Web Framework**: The project is built on [Stapler](https://stapler.jenkins.io/), a web framework that binds Java objects to URLs.
*   **Servlet Container**: It uses an embedded [Jetty](https://www.eclipse.org/jetty/) server via the Winstone servlet container.
*   **Testing**:
    *   [JUnit](https://junit.org/junit5/) is used for unit testing.
    *   [Jacoco](https://www.eclemma.org/jacoco/) is used for code coverage analysis.
*   **Static Analysis**:
    *   [SpotBugs](https://spotbugs.github.io/) for finding potential bugs in the code.
    *   [Checkstyle](https://checkstyle.sourceforge.io/) for enforcing coding standards.
*   **Templating**: [Jelly](https://commons.apache.org/proper/commons-jelly/), an XML-based scripting language, is used for creating views (UI pages).
*   **Parser Generation**: [ANTLR](https://www.antlr.org/) is used for creating language parsers.

### JavaScript

*   **Runtime Environment**: [Node.js](https://nodejs.org/) is used for the frontend build process.
*   **Package Manager**: [Yarn](https://yarnpkg.com/) is used to manage frontend dependencies.
*   **Build Tool**: [Webpack](https://webpack.js.org/) bundles JavaScript, CSS, and other assets for the browser.
*   **Transpiler**: [Babel](https://babeljs.io/) is used to transpile modern JavaScript code into a backward-compatible version.
*   **Core Libraries**:
    *   [jQuery](https://jquery.com/): A utility library for DOM manipulation.
    *   [Handlebars](https://handlebarsjs.com/): A templating engine for generating HTML.
    *   [Lodash](https://lodash.com/): A utility library providing helper functions.
*   **Linters & Formatters**:
    *   [ESLint](https://eslint.org/): For identifying and reporting on patterns in JavaScript code.
    *   [Prettier](https://prettier.io/): An opinionated code formatter.
    *   [Stylelint](https://stylelint.io/): For linting CSS and SCSS files.

### CSS

*   **Preprocessor**: [Sass (SCSS)](https://sass-lang.com/) is used for writing more maintainable CSS.
*   **Post-processor**: [PostCSS](https://postcss.org/) is used to transform CSS with JavaScript plugins.

### Groovy

*   **CI/CD**: The `Jenkinsfile` is written in Groovy for defining the Jenkins Pipeline.
*   **Scripting**: Used for helper scripts like `licenseCompleter.groovy`.

### Other Languages

*   **Shell Script**: Used for automation scripts like `ath.sh`.
*   **Perl**: Used for tooling, as seen in `translation-tool.pl`.
*   **Python**: Used for utility scripts like `update-since-todo.py`.
*   **Ruby**: Used for reporting scripts like `core/report-l10n.rb`.