FROM ubuntu:jammy-20230624
RUN apt-get update && \
    apt-get install -y software-properties-common curl && \
    mkdir -p /opt/openjdk
ARG JAVA_DOWNLOAD_URL=https://download.bell-sw.com/java/8u382+6/bellsoft-jdk8u382+6-linux-amd64.tar.gz
ENV JAVA_HOME /opt/openjdk
ENV PATH $JAVA_HOME/bin:$PATH
RUN cd /opt/openjdk && \
    curl -L $JAVA_DOWNLOAD_URL | tar zx --strip-components=1
