<a href="https://jenkins.io">
    <img width="400" src="https://www.jenkins.io/images/jenkins-logo-title-dark.svg" alt="Jenkins logo"> 
</a>

[![Jenkins Regular Release](https://img.shields.io/endpoint?url=https%3A%2F%2Fwww.jenkins.io%2Fchangelog%2Fbadge.json)](https://www.jenkins.io/changelog)
[![Jenkins LTS Release](https://img.shields.io/endpoint?url=https%3A%2F%2Fwww.jenkins.io%2Fchangelog-stable%2Fbadge.json)](https://www.jenkins.io/changelog-stable)
[![Docker Pulls](https://img.shields.io/docker/pulls/jenkins/jenkins.svg)](https://hub.docker.com/r/jenkins/jenkins/)
[![CII Best Practices](https://bestpractices.coreinfrastructure.org/projects/3538/badge)](https://bestpractices.coreinfrastructure.org/projects/3538)
[![Reproducible Builds](https://img.shields.io/badge/Reproducible_Builds-ok-green)](https://maven.apache.org/guides/mini/guide-reproducible-builds.html)
[![Gitter](https://img.shields.io/gitter/room/jenkinsci/jenkins)](https://app.gitter.im/#/room/#jenkinsci_jenkins:gitter.im)

---

# 📌 Table of Contents

- [About](#about)
- [What to Use Jenkins for and When to Use It](#what-to-use-jenkins-for-and-when-to-use-it)
- [Downloads](#downloads)
- [Getting Started (Development)](#getting-started-development)
- [Source](#source)
- [Contributing to Jenkins](#contributing-to-jenkins)
- [News and Website](#news-and-website)
- [Governance](#governance)
- [Adopters](#adopters)
- [License](#license)
- [Screenshots](#screenshots)

---

# About

In a nutshell, Jenkins is the leading open-source automation server.
Built with Java, it provides over 2,000 [plugins](https://plugins.jenkins.io/) to support automating virtually anything,
so that humans can spend their time doing things machines cannot.


# What to Use Jenkins for and When to Use It

Use Jenkins to automate your development workflow, so you can focus on work that matters most. Jenkins is commonly used for:

- Building projects
- Running tests to detect bugs and other issues as soon as they are introduced
- Static code analysis
- Deployment

Execute repetitive tasks, save time, and optimize your development process with Jenkins.

# Downloads

The Jenkins project provides official distributions as WAR files, Docker images, native packages and installers for platforms including several Linux distributions and Windows.
See the [Downloads](https://www.jenkins.io/download) page for references.

For all distributions Jenkins offers two release lines:

- [Weekly](https://www.jenkins.io/download/weekly/) -
  Frequent releases which include all new features, improvements, and bug fixes.
- [Long-Term Support (LTS)](https://www.jenkins.io/download/lts/) -
  Older release line which gets periodically updated via bug fix backports.

Latest releases:

[![Jenkins Regular Release](https://img.shields.io/endpoint?url=https%3A%2F%2Fwww.jenkins.io%2Fchangelog%2Fbadge.json)](https://www.jenkins.io/changelog)
[![Jenkins LTS Release](https://img.shields.io/endpoint?url=https%3A%2F%2Fwww.jenkins.io%2Fchangelog-stable%2Fbadge.json)](https://www.jenkins.io/changelog-stable)

# Getting Started (Development)

To build and run Jenkins locally from source, follow these steps:

### 1. Clone the Repository

```bash
git clone https://github.com/jenkinsci/jenkins.git
cd jenkins
```
### 2. Build the Project
Use Maven to compile and package Jenkins:
```bash
mvn clean install -DskipTests
```
To run tests during the build, omit the ```-DskipTests``` flag.
> The build may take several minutes, depending on your system and internet speed.

### 3. Run Jenkins
Once built, you can launch Jenkins using:
```bash
java -jar war/target/jenkins.war
```
Jenkins will start on http://localhost:8080 by default.
> ⚠️ Make sure Java 11 or higher and Apache Maven are installed and available in your PATH.

### 4. Developer Tips
- Use ```mvn hpi:run``` to start Jenkins in development mode with hot-reload support for plugins.
- Logs will be shown in the console to help with debugging.
- You can access the initial admin password in:
```bash
~/.jenkins/secrets/initialAdminPassword
```
If port 8080 is in use, run Jenkins on another port:
```bash
java -jar war/target/jenkins.war --httpPort=9090
```

### 5. Troubleshooting
OutOfMemoryError: Try increasing memory limits:
```bash
MAVEN_OPTS="-Xmx2g -XX:MaxPermSize=512m"
```
- Permission issues on Unix/macOS: use sudo or ensure correct ownership of files.
- Build fails on tests: Try a clean rebuild:
```bash
mvn clean install -DskipTests
```
### 6. Useful Commands

| Task                | Command                         |
| ------------------- | ------------------------------- |
| Clean build         | `mvn clean install -DskipTests` |
| Run with tests      | `mvn clean install`             |
| Start in dev mode   | `mvn hpi:run`                   |
| Run unit tests only | `mvn test`                      |
| Build WAR only      | `mvn package -DskipTests`       |


For more information on setting up your development environment, contributing, and working with Jenkins internals, check the official Jenkins Developer Documentation:
➡️ [https://www.jenkins.io/doc/developer/](https://www.jenkins.io/doc/developer/)


# Source

Our latest and greatest source of Jenkins can be found on [GitHub](https://github.com/jenkinsci/jenkins). Fork us!

# Contributing to Jenkins

New to open source or Jenkins? Here’s how to get started:

- Check our [Good First Issues](https://github.com/jenkinsci/jenkins/contribute)
- Join our Gitter chat for questions and help
- Read the [Contribution Guidelines](CONTRIBUTING.md)

For more information about participating in the community and contributing to the Jenkins project,
see [this page](https://www.jenkins.io/participate/).

Documentation for Jenkins core maintainers is in the [maintainers guidelines](docs/MAINTAINERS.adoc).

# News and Website

All information about Jenkins can be found on our [official website](https://www.jenkins.io/), including documentation, blog posts, plugin listings, community updates, and more.

Stay up-to-date with the latest Jenkins news, tutorials, and release notes:

- [Jenkins Blog](https://www.jenkins.io/blog/)
- [Documentation](https://www.jenkins.io/doc/)
- [Plugins Index](https://plugins.jenkins.io/)
- [Events](https://www.jenkins.io/events/)
- [Newsletter](https://www.jenkins.io/newsletter/)

Follow Jenkins on social media to stay connected with the community:

- [Twitter / X](https://twitter.com/jenkinsci)
- [YouTube](https://www.youtube.com/c/jenkinsci)
- [GitHub](https://github.com/jenkinsci/jenkins)

# Governance

The Jenkins project is governed as an open source community, led by a [governance board](https://www.jenkins.io/project/governance/) and supported by a large network of maintainers and contributors.

Our governance is based on the following principles:

- Open and transparent decision-making
- Community-driven leadership
- Empowering contributors and plugin maintainers

We encourage all participants to read and follow our:

- [Governance Document](https://www.jenkins.io/project/governance/)
- [Code of Conduct](https://www.jenkins.io/project/conduct/)
- [Security Policy](https://www.jenkins.io/security/)
- [Project Roadmap](https://www.jenkins.io/project/roadmap/)


# Adopters

Jenkins is trusted by **millions of users** and adopted by **thousands of companies** around the world — from startups to enterprises — to automate their software delivery pipelines.

Explore the [Adopters Page](https://www.jenkins.io/project/adopters/) to see:

- Companies and organizations using Jenkins
- Success stories and case studies
- How Jenkins is used in different industries

>  If your company uses Jenkins and you'd like to be featured, feel free to [submit your story](https://www.jenkins.io/project/adopters/#submit-your-story)!


# License

Jenkins is **licensed** under the **[MIT License](LICENSE.txt)**.

# Screenshots

### WebSite
<img width="1920" height="927" alt="image" src="https://github.com/user-attachments/assets/a810730e-85c4-40f9-ae1d-f69e94b36d16" />

### Documentation 
<img width="1920" height="927" alt="image" src="https://github.com/user-attachments/assets/b3548cc4-ff1b-4a92-94b2-10c7696153ef" />

