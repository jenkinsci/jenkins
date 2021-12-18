# Contributing to Jenkins

This page provides information about contributing code to the Jenkins core codebase.

:exclamation: There's a lot more to the Jenkins project than just code. For more information on the ways that you can contribute to the Jenkins project, see [Participate].

## Getting started

1. Fork the repository on GitHub
2. Clone the forked repository to your machine
3. Install the necessary development tools. In order to develop Jenkins, you need the following:
  * Java Development Kit (JDK) 11 or 8.
    In the Jenkins project we usually use [Eclipse Adoptium](https://adoptium.net/) or [OpenJDK](https://openjdk.java.net/), but you can use other JDKs as well.
  * Apache Maven 3.8.1 or above. You can [download Maven here].
    In the Jenkins project we usually use the most recent Maven release.
  * Any IDE which supports importing Maven projects.
  * Install [NodeJS](https://nodejs.org/en/). **Note:** only needed to work on the frontend assets found in the `war` module.
    * Frontend tasks are run using [yarn](https://yarnpkg.com/lang/en/). Run `npm install -g yarn` to install it.
4. Set up your development environment as described in [Preparing for Plugin Development]

If you want to contribute to Jenkins, or just learn about the project,
you can start by fixing some easier issues.
In the Jenkins issue tracker we mark such issues as `newbie-friendly`.
You can find them by using this query (check the link) for [newbie friendly issues].

## Building and Debugging

The Jenkins core build flow is built around Maven.
You can read a description of the [building and debugging process here].

If you want simply to build the `jenkins.war` file as fast as possible without tests, run:

```sh
mvn -am -pl war,bom -DskipTests -Dspotbugs.skip -Dspotless.check.skip clean install
```

The WAR file will be created in `war/target/jenkins.war`.
After that, you can start Jenkins using Java CLI ([guide]).
If you want to debug the WAR file without using Maven plugins,
You can run the executable with [Remote Debug Flags]
and then attach IDE Debugger to it.

To launch a development instance, after the above command, run:

```sh
mvn -pl war jetty:run
```

(Beware that `maven-plugin` builds will not work in this mode, due to class loading conflicts.)

### Building frontend assets

To work on the `war` module frontend assets, two processes are needed at the same time:

On one terminal, start a development server that will not process frontend assets:
```sh
mvn -pl war jetty:run -Dskip.yarn
```

On another terminal, move to the war folder and start a [webpack](https://webpack.js.org/) dev server:
```sh
cd war; yarn start
```

## Testing changes

Jenkins core includes unit and functional tests as a part of the repository.

Functional tests (`test` module) take a while to run, even on server-grade machines.
Most of the tests will be launched by the continuous integration instance,
so there is no strict need to run full test suites before proposing a pull request.

There are 3 profiles for tests:

* `light-test` - runs only unit tests, no functional tests
* `smoke-test` - runs unit tests + a number of functional tests
* `all-tests` - runs all tests, with re-run (default)

In addition to the included tests, you can also find extra integration and UI
tests in the [Acceptance Test Harness (ATH)] repository.
If you propose complex UI changes, you should create new ATH tests for them.

### JavaScript unit tests

In case there's only need to run the JS tests:
```sh
cd war; yarn test
```

## Proposing Changes

The Jenkins project source code repositories are hosted at GitHub.
All proposed changes are submitted, and code reviewed, using a [GitHub pull request] process.

To submit a pull request:

1. Commit your changes and push them to your fork on GitHub.
It is a good practice is to create branches instead of pushing to master.
2. In the GitHub Web UI, click the _New Pull Request_ button.
3. Select `jenkinsci` as _base fork_ and `master` as `base`, then click _Create Pull Request_.
  * We integrate all changes into the master branch towards the Weekly releases.
  * After that, the changes may be backported to the current LTS baseline by the LTS Team.
    Read more about the [backporting process].
4. Fill in the Pull Request description according to the [proposed template].
5. Click _Create Pull Request_.
6. Wait for CI results/reviews, process the feedback.
  * If you do not get feedback after 3 days, feel free to ping `@jenkinsci/core-pr-reviewers` in the comments.
  * Usually we merge pull requests after 2 approvals from reviewers, no requested changes, and having waited some more time to give others an opportunity to provide their feedback.
    See [this page](/docs/MAINTAINERS.adoc) for more information about our review process.

Once your Pull Request is ready to be merged,
the repository maintainers will integrate it, prepare changelogs, and
ensure it gets released in one of upcoming Weekly releases.
There is no additional action required from pull request authors at this point.

## IntelliJ suggestion

In case you are using IntelliJ, please adjust the default setting in respect to whitespace fixes on save.
The setting can be found in Settings -> Editor -> General -> On Save -> Remove trailing spaces on: `Modified lines`
This will help minimize the diff, which makes reviewing PRs easier.

We also do not recommend `*` imports in the production code.
Please disable them in Settings > Editor > Codestyle > Java by setting _Class count to use import with '*'_ and Names count to use import with '*'_ to a high value, e.g. 100. 

## Copyright

The Jenkins core is licensed under [MIT license], with a few exceptions in bundled classes.
We consider all contributions as MIT unless it's explicitly stated otherwise.
MIT-incompatible code contributions will be rejected.
Contributions under MIT-compatible licenses may also be rejected if they are not ultimately necessary.

We **Do NOT** require pull request submitters to sign the [contributor agreement]
as long as the code is licensed under MIT, and merged by one of the contributors with the signed agreement.

We still encourage people to sign the contributor agreement if they intend to submit more than a few pull requests.
Signing is also a mandatory prerequisite for getting merge/push permissions to core repositories
and for joining teams like the [Jenkins Security Team].

## Continuous Integration

The Jenkins project has a Continuous Integration server... powered by Jenkins, of course.
It is located at [ci.jenkins.io].

The Jenkins project uses [Jenkins Pipeline] to run builds.
The code for the core build flow is stored in the [Jenkinsfile] in the repository root.
If you want to update that build flow (e.g. "add more checks"),
just submit a pull request.

# Links

* [Jenkins Contribution Landing Page](https://www.jenkins.io/participate/)
* [Jenkins Chat Channels](https://www.jenkins.io/chat/)
* [Beginners Guide To Contributing](https://www.jenkins.io/participate/)
* [List of newbie-friendly issues in the core](https://issues.jenkins.io/issues/?jql=project%20%3D%20JENKINS%20AND%20status%20in%20(Open%2C%20%22In%20Progress%22%2C%20Reopened)%20AND%20component%20%3D%20core%20AND%20labels%20in%20(newbie-friendly))

[Preparing for Plugin Development]: https://www.jenkins.io/doc/developer/tutorial/prepare/
[newbie friendly issues]: https://issues.jenkins.io/issues/?jql=project%20%3D%20JENKINS%20AND%20status%20in%20(Open%2C%20%22In%20Progress%22%2C%20Reopened)%20AND%20component%20%3D%20core%20AND%20labels%20in%20(newbie-friendly)
[Participate]: https://www.jenkins.io/participate/
[building and debugging process here]: https://www.jenkins.io/doc/developer/building/
[guide]: https://www.jenkins.io/doc/book/installing/war-file/#run-the-war-file
[Remote Debug Flags]: https://stackoverflow.com/questions/975271/remote-debugging-a-java-application
[Acceptance Test Harness (ATH)]: https://github.com/jenkinsci/acceptance-test-harness
[backporting process]: https://www.jenkins.io/download/lts/
[proposed template]: .github/PULL_REQUEST_TEMPLATE.md
[MIT license]: ./LICENSE.txt
[contributor agreement]: https://www.jenkins.io/project/governance/#cla
[Jenkins Security Team]: https://www.jenkins.io/security/#team
[ci.jenkins.io]: https://ci.jenkins.io/
[Jenkins Pipeline]: https://www.jenkins.io/doc/book/pipeline/
[Jenkinsfile]: ./Jenkinsfile
[download Maven here]: https://maven.apache.org/download.cgi
[GitHub pull request]: https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/about-pull-requests