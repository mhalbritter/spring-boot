FROM redhat/ubi9:9.2-722
ARG JAVA_DOWNLOAD_URL=https://github.com/bell-sw/Liberica/releases/download/17.0.8%2B7/bellsoft-jdk17.0.8+7-linux-amd64.tar.gz
ENV JAVA_HOME /opt/openjdk
ENV PATH $JAVA_HOME/bin:$PATH
RUN mkdir -p /opt/openjdk && \
    cd /opt/openjdk && \
    curl -L $JAVA_DOWNLOAD_URL | tar zx --strip-components=1
