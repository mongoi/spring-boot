FROM redhat/ubi9:9.2-722
ARG JAVA_DOWNLOAD_URL=https://download.bell-sw.com/java/8u382+6/bellsoft-jdk8u382+6-linux-amd64.tar.gz
ENV JAVA_HOME /opt/openjdk
ENV PATH $JAVA_HOME/bin:$PATH
RUN mkdir -p /opt/openjdk && \
    cd /opt/openjdk && \
    curl -L $JAVA_DOWNLOAD_URL | tar zx --strip-components=1
