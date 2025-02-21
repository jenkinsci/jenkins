FROM gitpod/workspace-full:latest
ARG JAVA_VERSION=21.0.6-tem
ARG MAVEN_VERSION=3.9.9
# Install Java 21
RUN bash -c ". /home/gitpod/.sdkman/bin/sdkman-init.sh && \
    sdk install java ${JAVA_VERSION} && \
    sdk default java ${JAVA_VERSION}"
# Install Maven
RUN bash -c ". /home/gitpod/.sdkman/bin/sdkman-init.sh && \
    sdk install maven ${MAVEN_VERSION} && \
    sdk default maven ${MAVEN_VERSION}"
# Install GitHub CLI
RUN brew install gh
