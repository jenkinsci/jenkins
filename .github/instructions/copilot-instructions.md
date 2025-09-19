# Jenkins Core Development

This document provides instructions for AI coding agents to contribute to the Jenkins core codebase.

## About this project

This is the source code for the Jenkins core, the heart of the Jenkins automation server. It is a multi-module Maven project. The main components are in the `core` directory, and the final `jenkins.war` file is assembled in the `war` directory.

## Architecture

The Jenkins architecture is based on a modular system. Key modules in this repository include:

- `core`: The main module containing the core Jenkins functionality. Business logic is implemented here. Key packages include:
    - `hudson.model`: Core data models like `Job`, `Run`, `User`.
    - `jenkins.model`: The main `Jenkins` singleton class.
- `war`: Assembles the final `jenkins.war` file for distribution. It packages the core module along with other dependencies.
- `cli`: Contains the code for the Jenkins command-line interface.
- `test`: Contains the test harness and utility classes for testing Jenkins.

## Development Workflow

### Building

The project is built with Maven. The main command to build the project is:

```sh
mvn clean install
```

This will build all modules and create the `jenkins.war` file in `war/target/`.

To speed up the build, you can skip tests:

```sh
mvn -DskipTests clean install
```

### Running tests

To run all tests, use:

```sh
mvn test
```

To run a specific test, you can use the `-Dtest` parameter. For example, to run tests in `MyTest.java`:

```sh
mvn test -Dtest=MyTest
```

### Running Jenkins locally

You can run a development version of Jenkins using the following command from the root directory:

```sh
mvn -f war/pom.xml jetty:run
```

This will start Jenkins on `http://localhost:8080/jenkins`.

### Static Analysis

The project uses SpotBugs and Checkstyle for static analysis. These are run as part of the Maven build. You can find the configuration in `src/checkstyle/` and in the `pom.xml` files.

## Key Directories

- `core/src/main/java`: The main source code for Jenkins core.
- `war/src/main/webapp`: The web application resources for the Jenkins UI.
- `test/src/test/java`: The source code for the tests.
- `pom.xml`: The root Maven POM file that defines the project structure and build process. Each module also has its own `pom.xml`.
- `Jenkinsfile`: The definition for the Jenkins CI/CD pipeline for this project.
