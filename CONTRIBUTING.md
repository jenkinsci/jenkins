# Contributing to Jenkins

This page provides information about contributing code to the Jenkins core codebase.

:exclamation: There's a lot more to the Jenkins project than just code. For more information on the ways that you can contribute to the Jenkins project, see [Participate](https://www.jenkins.io/participate/).

## Getting started

1. Fork the repository on GitHub
2. Clone the forked repository to your machine
3. Install the necessary development tools. In order to develop Jenkins, you need the following:
   - Java Development Kit (JDK) 17 or 21.
     In the Jenkins project we usually use [Eclipse Temurin](https://adoptium.net/) or [OpenJDK](https://openjdk.java.net/), but you can use other JDKs as well.
   - Apache Maven 3.9.6 or above. You can [download Maven here](https://maven.apache.org/download.cgi).
     In the Jenkins project we usually use the most recent Maven release.
   - Any IDE which supports importing Maven projects.
4. Set up your development environment as described in [Preparing for Plugin Development](https://www.jenkins.io/doc/developer/tutorial/prepare/)

If you want to contribute to Jenkins, or just learn about the project,
you can start by fixing some easier issues.
In the Jenkins issue tracker we mark such issues as `newbie-friendly`.
You can find them by using this query (check the link) for [newbie friendly issues](<https://issues.jenkins.io/issues/?jql=project%20%3D%20JENKINS%20AND%20status%20in%20(Open%2C%20%22In%20Progress%22%2C%20Reopened)%20AND%20component%20%3D%20core%20AND%20labels%20in%20(newbie-friendly)>).

## Building and Debugging

The Jenkins core build flow is built around Maven.
You can read a description of the [building and debugging process here](https://www.jenkins.io/doc/developer/building/).

### Building the WAR file

If you want simply to build the `jenkins.war` file as fast as possible without tests, run:

```sh
mvn -am -pl war,bom -Pquick-build clean install
```

The WAR file will be created in `war/target/jenkins.war`.
After that, you can start Jenkins using Java CLI ([guide](https://www.jenkins.io/doc/book/installing/war-file/#run-the-war-file)).
If you want to debug the WAR file without using Maven plugins,
You can run the executable with [Remote Debug Flags](https://stackoverflow.com/questions/975271/remote-debugging-a-java-application)
and then attach IDE Debugger to it.

### Launching a development instance

To launch a development instance, after [building the WAR file](#building-the-war-file), run:

```sh
MAVEN_OPTS='--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED' mvn -pl war jetty:run
```

(Beware that `maven-plugin` builds will not work in this mode, due to class loading conflicts.)

### Running the Yarn frontend build

> [!TIP]
> If you already have Node.js installed, you do not need to change your path. Start using Yarn by enabling [Corepack](https://yarnpkg.com/corepack) with `corepack enable`, if it isn't already; this will add the `yarn` binary to your path.

To run the Yarn frontend build, after [building the WAR file](#building-the-war-file), add the downloaded versions of Node and Yarn to your path:

```sh
export PATH=$PWD/node:$PWD/node/node_modules/corepack/shims:$PATH
```

Then you can run Yarn with e.g.

```sh
yarn
```

### Building frontend assets

To work on the `war` module frontend assets, two processes are needed at the same time:

On one terminal, start a development server that will not process frontend assets:

```sh
MAVEN_OPTS='--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED' mvn -pl war jetty:run -Dskip.yarn
```

Open another terminal and start a [webpack](https://webpack.js.org/) dev server, after [optionally adding Node and Yarn to your path](#running-the-yarn-frontend-build):

```sh
yarn start
```

### Gitpod

You can open this project as a [Gitpod workspace](https://www.gitpod.io/) which comes pre-configured with all the tools you will need.
You can use IntelliJ IDEA (preferred) or VS Code (alternate) in the browser.

[![Open in Gitpod](https://gitpod.io/button/open-in-gitpod.svg)](https://gitpod.io/#https://github.com/jenkinsci/jenkins)

If you prefer using IntelliJ IDEA, you can setup Gitpod integration with JetBrains Gateway using the instructions on [gitpod.io](https://www.gitpod.io/docs/ides-and-editors/intellij),
which will open the workspace in IntelliJ IDEA using JetBrains Gateway.

### Linting

For linting we use a number of tools:

- [checkstyle](https://checkstyle.sourceforge.io/)
- [eslint](https://eslint.org/)
- [prettier](https://prettier.io/)
- [spotless](https://github.com/diffplug/spotless)
- [stylelint](https://stylelint.io/)

These are all configured to run as part of the Maven build, although they will be skipped if you are building with the `quick-build` profile.

To automatically fix backend issues, run:

```sh
mvn spotless:apply
```

To view frontend issues, after [optionally adding Node and Yarn to your path](#running-the-yarn-frontend-build), run:

```sh
yarn lint
```

To fix frontend issues, after [optionally adding Node and Yarn to your path](#running-the-yarn-frontend-build), run:

```sh
yarn lint:fix
```

## Testing changes

Jenkins core includes unit and functional tests as a part of the repository.

Functional tests (`test` module) take a while to run, even on server-grade machines.
Most of the tests will be launched by the continuous integration instance,
so there is no strict need to run full test suites before proposing a pull request.

There are 3 profiles for tests:

- `light-test` - runs only unit tests, no functional tests
- `smoke-test` - runs unit tests + a number of functional tests
- `all-tests` - runs all tests, with re-run (default)

In addition to the included tests, you can also find extra integration and UI
tests in the [Acceptance Test Harness (ATH)](https://github.com/jenkinsci/acceptance-test-harness) repository.
If you propose complex UI changes, you should create new ATH tests for them.

## Proposing Changes

The Jenkins project source code repositories are hosted at GitHub.
All proposed changes are submitted, and code reviewed, using a [GitHub pull request](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/about-pull-requests) process.

To submit a pull request:

1. Commit your changes and push them to your fork on GitHub.
   It is a good practice is to create branches instead of pushing to master.
2. In the GitHub Web UI, click the _New Pull Request_ button.
3. Select `jenkinsci` as _base fork_ and `master` as `base`, then click _Create Pull Request_.
   - We integrate all changes into the master branch towards the Weekly releases.
   - After that, the changes may be backported to the current LTS baseline by the LTS Team.
     Read more about the [backporting process](https://www.jenkins.io/download/lts/).
4. Fill in the Pull Request description according to the [proposed template](.github/PULL_REQUEST_TEMPLATE.md).
5. Click _Create Pull Request_.
6. Wait for CI results/reviews, process the feedback.
   - If you do not get feedback after 3 days, feel free to ping `@jenkinsci/core-pr-reviewers` in the comments.
   - Usually we merge pull requests after 2 approvals from reviewers, no requested changes, and having waited some more time to give others an opportunity to provide their feedback.
     See [this page](/docs/MAINTAINERS.adoc) for more information about our review process.

Once your Pull Request is ready to be merged,
the repository maintainers will integrate it, prepare changelogs, and
ensure it gets released in one of upcoming Weekly releases.
There is no additional action required from pull request authors at this point.

### Pull request management

The Jenkins project uses a well-defined set of labels to mark the status and content of pull requests.
The complete list of labels can be found at https://github.com/jenkinsci/jenkins/labels.
These labels are defined as follows:

- `needs-docs` marks a pull request as lacking documentation, either for developers (e.g., Javadoc) or users (e.g., changes to the [Jenkins handbook](https://www.jenkins.io/doc/book/)).
  For such pull requests to be approved and merged, the corresponding changes to the documentation should be proposed.
  If those changes belong to a separate repository (e.g., `jenkins-infra/jenkins.io`), a secondary pull request should be created in draft state in the other repository and reviewed in tandem with the primary pull request that proposes the code change.
- `needs-fix` marks a pull request which has pending requests for change that have not yet been addressed.
  Such pull requests will not be merged until the code has been fixed and the tests pass.
- `needs-justification` marks a pull request where the reasoning is unclear, incomplete or not entirely cogent.
  To properly evaluate the solution provided in a pull request, maintainers must be able to understand the high-level problem that the pull request attempts to solve.
  While the context might be obvious to the author, it is not always apparent to reviewers and maintainers.
  The use of design documents, high-level tracking epics, [minimal reproducible examples (MREs)](https://en.wikipedia.org/wiki/Minimal_reproducible_example), etc. is strongly encouraged.
- `needs-more-review` marks a pull request as lacking a sufficient number of reviews from subject-matter expert(s) (SME), either because the changes are complex and not sufficiently explained or because there is a lack of consensus regarding the proposed solution.
- `on-hold` marks a pull request that depends on another event and cannot be merged until the completion of that event.
  When the dependent task has been completed, the pull request will be ready for merge.
- `proposed-for-close` marks a pull request where there is either no consensus on the next steps or where the next steps have not been taken and an extended period of time has elapsed.
  Such pull requests are typically closed approximately one week after the label has been applied.
  They can always be reopened once consensus has been reached on the next steps or when action is taken regarding these next steps.
- `ready-for-merge` marks a pull request that has met the acceptance criteria, as defined elsewhere in this document.
  If there is no negative feedback, such pull requests are typically merged within approximately 24 hours.
- `stalled` marks a pull request that is off to a promising start but requires additional effort to reach completion - effort that appears to have been abandoned.
  If the original author lacks the time and interest to continue the original effort, we suggest that someone else pick up where the original author left off to drive the effort to completion.
- `work-in-progress` marks a pull request that remains under active development.
  Such pull requests are not ready for final review.

To ensure that pull requests are processed efficiently, the `ready-for-merge`, `stalled`, and `proposed-for-close` labels are subject to time constraints.

A pull request labeled with `ready-for-merge` is merged after approximately 24 hours if there is no negative feedback.

If a pull request has remained incomplete with no activity for over a month, we will make this explicit by labeling the PR as `stalled`.

If a pull request labelled as `stalled` remains inactive for yet another month, we will label it as `proposed-for-close` in order to maintain an orderly PR queue.
Approximately one week after this label is applied to a pull request, it will be closed.

While contributors are strongly encouraged to drive PRs to completion on their own, we recognize that in some situations the help of a maintainer can be valuable.
Yet also, we recognize that some contributors prefer not to receive unsolicited changes.
When opening a pull request, enabling the _Allow edits by maintainers_ option indicates that you accept that maintainers may push new commits into your pull request branch.
As some maintainers are willing to fix typographical errors and merge conflicts while reviewing pull requests, accepting edits from maintainers can speed up the integration of your pull request.

## IntelliJ suggestion

In case you are using IntelliJ, please adjust the default setting in respect to whitespace fixes on save.
The setting can be found in Settings -> Editor -> General -> On Save -> Remove trailing spaces on: `Modified lines`
This will help minimize the diff, which makes reviewing PRs easier.

We also do not recommend `*` imports in the production code.
Please disable them in Settings > Editor > Codestyle > Java by setting _Class count to use import with '\*'_ and Names count to use import with '\*'\_ to a high value, e.g. 100.

The addition of `@{jenkins.addOpens}` to `argLine` exposes a bug in IntelliJ IDEA.
A patch has been proposed in [JetBrains/intellij-community#1976](https://github.com/JetBrains/intellij-community/pull/1976).
Pending the merge and release of this patch, IntelliJ IDEA users should work around the problem as follows:

1. Go to **Settings** > **Build, Execution, Deployment** > **Build Tools** > **Maven** > **Running Tests**.
2. Under "Pass to JUnit process [the] following `maven-surefire-plugin` and `maven-failsafe-plugin` settings", uncheck `argLine`.

Failure to work around the problem as described above will result in a `could not open '{jenkins.addOpens}'` failure when running tests in IntelliJ IDEA.

### Code formatting for frontend files

Install the [Prettier plugin](https://www.jetbrains.com/help/idea/prettier.html).
Follow the instructions on the above JetBrains page to configure it how you wish. 'On code reformatting' is a good option.

## Copyright

The Jenkins core is licensed under [MIT license](./LICENSE.txt), with a few exceptions in bundled classes.
We consider all contributions as MIT unless it's explicitly stated otherwise.
MIT-incompatible code contributions will be rejected.
Contributions under MIT-compatible licenses may also be rejected if they are not ultimately necessary.

We **Do NOT** require pull request submitters to sign the [contributor agreement](https://www.jenkins.io/project/governance/#cla)
as long as the code is licensed under MIT, and merged by one of the contributors with the signed agreement.

We still encourage people to sign the contributor agreement if they intend to submit more than a few pull requests.
Signing is also a mandatory prerequisite for getting merge/push permissions to core repositories
and for joining teams like the [Jenkins Security Team](https://www.jenkins.io/security/#team).

## Continuous Integration

The Jenkins project has a Continuous Integration server... powered by Jenkins, of course.
It is located at [ci.jenkins.io](https://ci.jenkins.io/).

The Jenkins project uses [Jenkins Pipeline](https://www.jenkins.io/doc/book/pipeline/) to run builds.
The code for the core build flow is stored in the [Jenkinsfile](./Jenkinsfile) in the repository root.
If you want to update that build flow (e.g. "add more checks"),
just submit a pull request.

# Links

- [Jenkins Contribution Landing Page](https://www.jenkins.io/participate/)
- [Jenkins Chat Channels](https://www.jenkins.io/chat/)
- [Beginners Guide To Contributing](https://www.jenkins.io/participate/)
- [List of newbie-friendly issues in the core](<https://issues.jenkins.io/issues/?jql=project%20%3D%20JENKINS%20AND%20status%20in%20(Open%2C%20%22In%20Progress%22%2C%20Reopened)%20AND%20component%20%3D%20core%20AND%20labels%20in%20(newbie-friendly)>)
