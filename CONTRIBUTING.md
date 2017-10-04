# Contributing to Jenkins

:exclamation: For information on contributing to the Jenkins project, check out https://jenkins.io/redirect/contribute/. That page will help you get started.

Information below provides information about contributing to the Jenkins core codebase only.

## Getting started

1. Fork the repository on GitHub
2. Clone the forked repository to your machine
3. Install the development tools. In order to develop Jenkins, you need the following tools...
  * Maven 3.3.9 or above. You can download it [here](https://maven.apache.org/download.cgi)
  * Java Development Kit (JDK) 8. 
    - In Jenkins project we usually use [OpenJDK](http://openjdk.java.net/), 
  but you can use other JDKs as well.
    - Java 9 is **not supported** in Jenkins.
  * Any IDE, which supports importing Maven projects
4. Setup the environment as described in the [Plugin Development Guide](https://wiki.jenkins.io/display/JENKINS/Plugin+tutorial#Plugintutorial-SettingUpEnvironment)

If you want to contribute to Jenkins and to study the project, 
you could start contributing from fixing some low-hanging fruits.
In Jenkins issue tracker we mark such issues as `newbie-friendly`, you can find them
using [this query](https://issues.jenkins-ci.org/issues/?jql=project%20%3D%20JENKINS%20AND%20status%20in%20(Open%2C%20%22In%20Progress%22%2C%20Reopened)%20AND%20component%20%3D%20core%20AND%20labels%20in%20(newbie-friendly)).

## Building and Debugging

The entire build flow in the core is built around Maven. 
Building and debugging process is described [here](https://wiki.jenkins-ci.org/display/JENKINS/Building+Jenkins).

If you want simply to have the `jenkins.war` file as fast as possible without tests, run:

    mvn clean package -pl war -am -DskipTests

The WAR file will be created in `war/target/jenkins.war`. 
After that you can just start Jenkins using Java CLI ([guide](https://wiki.jenkins.io/display/JENKINS/Starting+and+Accessing+Jenkins)).
If you want to debug this WAR file without using Maven plugins,
You can just start the executable with [Remote Debug Flags](https://stackoverflow.com/questions/975271/remote-debugging-a-java-application) 
and then attach IDE Debugger to it.

## Testing changes

Jenkins core offers unit and functional tests as a part of the repository.

Functional tests (`test` module) take a while even on server-grade machines.
The most of the tests will be launched by the continuous integration instance,
so there is no strict need to run full test suites before proposing a pull request.

In addition to the included tests, you can also find extra integration and UI 
tests in the [Acceptance Test Harness (ATH)](https://github.com/jenkinsci/acceptance-test-harness) repository.
If you propose complex UI changes, it is advised to create new ATH tests for them.

## Proposing Changes

Jenkins project uses GitHub as a main engine for patch proposals and code reviews.
All proposals should be submitted as pull requests.
In order to do that...

1. Commit changes and push them to your fork on GitHub. 
It is a good practice is to create branches instead of pushing to master.
2. In GitHub Web UI click the _New Pull Request_ button
3. Select `jenkinsci` as _base fork_ and `master` as `base`, then click _Create Pull Request_
  * We integrate all changes into the master branch towards the Weekly releases
  * After that the changes may be backported to the current LTS baseline by the LTS Team.
    The backporting process is described [here](https://jenkins.io/download/lts/).
4. Fill in the Pull Request description according to the [proposed template](.github/PULL_REQUEST_TEMPLATE.md).
5. Click _Create Pull Request_
6. Wait for CI results/reviews, process the feedback.
  * If you do not get feedback after 3 days, feel free to ping `@jenkinsci/code-reviewers` to CC.
  * Usually we merge pull requests after 2 votes from reviewers or after 1 vote and 1-week delay without extra reviews.

Once your Pull Request is ready to be merged, 
the repository maintainers will integrate it, prepare changelogs and 
ensure it gets released in one of incoming Weekly releases.
There is no extra action items required from pull request authors at this point.

## Copyright

Jenkins core is licensed under MIT license, with few exceptions in the bundled classes.

We **Do NOT** require pull request submitters to sign the [contributor agreement](https://wiki.jenkins.io/display/JENKINS/Copyright+on+source+code)
while the code is licensed under MIT and merged by one of the contributors with the signed agreement.

We still encourage people to sign contributor agreements if they do many commits.
It is also a mandatory prerequisite for getting merge/push permissions to core repositories 
and for joining teams like [Jenkins Security Team](https://jenkins.io/security/#team).

## Continuous Integration

Jenkins project has a Continuous Integration server... powered by Jenkins, of course.
It is located [here](https://ci.jenkins.io/).

Jenkins project uses [Jenkins Pipeline](https://jenkins.io/doc/book/pipeline/) to run builds.
The core build flow is described in [Jenkinsfile](./Jenkinsfile) in the repository root.
If you want to update the build flow (e.g. "add more checks"),
you can also propose pull requests.

# Links

* [Jenkins Contribution Landing Page](https://jenkins.io/redirect/contribute/)
* [Beginners Guide To Contributing](https://wiki.jenkins.io/display/JENKINS/Beginners+Guide+to+Contributing)
* [List of newbie-friendly issues in the core](https://issues.jenkins-ci.org/issues/?jql=project%20%3D%20JENKINS%20AND%20status%20in%20(Open%2C%20%22In%20Progress%22%2C%20Reopened)%20AND%20component%20%3D%20core%20AND%20labels%20in%20(newbie-friendly))


