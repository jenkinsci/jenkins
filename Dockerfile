# Build on Java 10 is not supported/tested so far
FROM maven:3.5.3-jdk-8 as builder

COPY .mvn/ /jenkins/src/.mvn/
COPY cli/ /jenkins/src/cli/
COPY core/ /jenkins/src/core/
COPY src/ /jenkins/src/src/
COPY test/ /jenkins/src/test/
COPY war/ /jenkins/src/war/
COPY *.xml /jenkins/src/
COPY LICENSE.txt /jenkins/src/LICENSE.txt
COPY licenseCompleter.groovy /jenkins/src/licenseCompleter.groovy
COPY show-pom-version.rb /jenkins/src/show-pom-version.rb

WORKDIR /jenkins/src/
RUN mvn clean install --batch-mode -Psmoke-test

# The image is based on https://github.com/jenkinsci/docker/tree/java10
# All documentation is applicable
FROM jenkins/jenkins-experimental:2.127-jdk10

LABEL Description="This is an experimental image for Jenkins on Java 10"

COPY --from=builder /jenkins/src/war/target/jenkins.war /usr/share/jenkins/jenkins.war
COPY docker/jenkins2.sh /usr/local/bin/jenkins2.sh
ENTRYPOINT ["tini", "--", "/usr/local/bin/jenkins2.sh"]
