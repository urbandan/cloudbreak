FROM docker-private.infra.cloudera.com/cloudera_base/openjdk:11.0.6-jdk-slim-buster
MAINTAINER info@hortonworks.com

# REPO URL to download jar
ARG REPO_URL=https://repo.hortonworks.com/content/repositories/releases
ARG VERSION=''

ENV VERSION ${VERSION}

WORKDIR /

# install the cloudbreak app
ADD ${REPO_URL}/com/sequenceiq/cloudbreak/$VERSION/infrastructure-mock-$VERSION.jar /infrastructure-mock.jar

# add jmx exporter
ADD jmx_prometheus_javaagent-0.10.jar /jmx_prometheus_javaagent.jar

# extract schema files
#RUN jar xf infrastructure-mock.jar && mv /BOOT-INF/classes/schema/ /schema/

# Install starter script for the Cloudbreak application
COPY bootstrap/start_infrastructure_mock_app.sh /
COPY bootstrap/wait_for_infrastructure_mock_api.sh /

ENTRYPOINT ["/start_infrastructure_mock_app.sh"]
