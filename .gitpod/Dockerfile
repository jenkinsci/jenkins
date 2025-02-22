FROM gitpod/workspace-full:latest
ARG JAVA_VERSION=21.0.6-tem
ARG MAVEN_VERSION=3.9.9
# Install Java 21, Maven and GitHub CLI
RUN bash -c ". /home/gitpod/.sdkman/bin/sdkman-init.sh && \
    sdk install java ${JAVA_VERSION} && \
    sdk default java ${JAVA_VERSION} && \
    sdk install maven ${MAVEN_VERSION} && \
    sdk default maven ${MAVEN_VERSION} && \
    brew install gh"
