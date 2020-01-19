# DockerExtension JUnit5 
The extension to JUnit 5 that runs tests in Docker container using Maven. 

Parameters are read from `docker-extension.properties` file. You can specify a custom Docker image, specify a custom Maven test command, mount M2 from host machine, enable debugging etc.

This library uses Testcontainers to manage Docker environment.

## How to use it?
1. Add dependency
```xml
<dependency>
    <groupId>pl.beone.lib</groupId>
    <artifactId>docker-extension-junit5</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
```
2. Annotate a test class with `@ExtendWith(DockerExtension::class)`
3. Add `Dockerfile` file (`docker.test.image.custom.docker.dockerfile.name`) to `/src/docker` folder (`docker.test.image.custom.docker.directory.path`). Example file:
```dockerfile
FROM azul/zulu-openjdk-centos:11.0.5

RUN echo "en_US.UTF-8 UTF-8" >> /etc/locale.gen && \
    localedef --quiet -c -i en_US -f UTF-8 en_US.UTF-8

ENV LANG en_US.UTF-8
ENV LANGUAGE en_US
ENV LC_ALL en_US.UTF-8

ENV MAVEN_VERSION 3.6.3

RUN yum install -y wget && \
    # Maven
    cd opt && \
    wget https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz && \
    tar xzf apache-maven-$MAVEN_VERSION-bin.tar.gz && \
    rm -f apache-maven-$MAVEN_VERSION-bin.tar.gz

ENV JRE_HOME /usr/lib/jvm/zulu-11
ENV JAVA_HOME /usr/lib/jvm/zulu-11
ENV M2_HOME /opt/apache-maven-$MAVEN_VERSION
ENV PATH ${M2_HOME}/bin:${PATH}

RUN yum clean all && rm -rf /tmp/* /var/tmp/* /var/cache/yum/*
```
4. Add `docker-extension.properties` file to test resources folder (`src/test/resources`)
```properties
# Project root path to mount in Docker container in "docker.test.project.container.path" location
docker.test.project.path=${basedir}
# Mount path in Docker container
docker.test.project.container.path=/test

# Maven test command to run in Docker container
docker.test.maven.container.test.command=surefire:test -DtrimStackTrace=false
# Command to run after test execution in Docker container
docker.test.maven.container.test.run-after=chown -R 1000:1000 ${docker.test.project.container.path}

# Name of Docker image. Maven has to be installed
docker.test.image.name=
# Build custom Docker image
docker.test.image.custom.enabled=true
# Name of custom Docker image
docker.test.image.custom.name=${artifactId}:test
# Path to folder where Dockerfile file is located. It is used as build context
docker.test.image.custom.docker.directory.path=${basedir}/src/docker
# Name of Dockerfile file in "docker.test.image.custom.docker.directory.path" folder
docker.test.image.custom.docker.dockerfile.name=Dockerfile
# Parameters "docker.test.image.custom.docker-module.directory.path" and "docker.test.image.custom.docker-module.dockerfile-fragment.name" are optional
# Path to folder where Dockerfile-fragment file is located. It is added to context folder
docker.test.image.custom.docker-module.directory.path=
# Name of Dockerfile-fragment file in "docker.test.image.custom.docker-module.directory.path" folder
# Replaces "${DOCKERFILE-FRAGMENT}" in "docker.test.image.custom.docker.dockerfile.name" Dockerfile file
docker.test.image.custom.docker-module.dockerfile-fragment.name=
# Delete custom Docker image after test execution
docker.test.image.custom.deleteOnExit=false

# Mount M2 folder from host machine
docker.test.m2.mount.enabled=true
# Path of M2 folder on host machine
docker.test.m2.mount.path=${settings.localRepository}/..
# Mount path in Docker container
docker.test.m2.container.mount.path=/root/.m2

# Enable debugging mode
docker.test.debugger.enabled=false
# Debugging port
docker.test.debugger.port=5005
```
5. Resolve variables in `docker-extension.properties` and copy it to the test output directory
```xml
<build>
    ...
    <testResources>
        <testResource>
            <directory>src/test/resources</directory>
            <excludes>
                <exclude>docker-extension.properties</exclude>
            </excludes>
        </testResource>
        <testResource>
            <directory>src/test/resources</directory>
            <filtering>true</filtering>
            <includes>
                <include>docker-extension.properties</include>
            </includes>
        </testResource>
    </testResources>
    ...
</build>
```
6. Run a test