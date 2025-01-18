FROM gitpod/workspace-full

ARG MAVEN_VERSION=3.9.9

RUN brew install gh && \
    bash -c ". /home/gitpod/.sdkman/bin/sdkman-init.sh && sdk install maven ${MAVEN_VERSION} && sdk default maven ${MAVEN_VERSION}"
