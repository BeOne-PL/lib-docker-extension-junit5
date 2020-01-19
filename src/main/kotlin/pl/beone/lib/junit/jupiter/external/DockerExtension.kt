package pl.beone.lib.junit.jupiter.external

import org.junit.jupiter.api.extension.*
import pl.beone.lib.junit.jupiter.applicationmodel.DockerExtensionException
import pl.beone.lib.junit.jupiter.internal.Configuration
import java.io.File
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs tests in Docker container using Maven.
 *
 * Parameters are read from `docker-extension.properties` file located in `src/test/resources`.
 * ```
 * # Project root path to mount in Docker container in "docker.test.project.container.path" location
 * docker.test.project.path=${basedir}
 * # Mount path in Docker container
 * docker.test.project.container.path=/test
 *
 * # Maven test command to run in Docker container
 * docker.test.maven.container.test.command=surefire:test -DtrimStackTrace=false
 * # Command to run after test execution in Docker container
 * docker.test.maven.container.test.run-after=chown -R 1000:1000 ${docker.test.project.container.path}
 *
 * # Name of Docker image. Maven has to be installed
 * docker.test.image.name=
 * # Build custom Docker image
 * docker.test.image.custom.enabled=true
 * # Name of custom Docker image
 * docker.test.image.custom.name=${artifactId}:test
 * # Path to folder where Dockerfile file is located. It is used as build context
 * docker.test.image.custom.docker.directory.path=${basedir}/src/docker
 * # Name of Dockerfile file in "docker.test.image.custom.docker.directory.path" folder
 * docker.test.image.custom.docker.dockerfile.name=Dockerfile
 * # Parameters "docker.test.image.custom.docker-module.directory.path" and "docker.test.image.custom.docker-module.dockerfile-fragment.name" are optional
 * # Path to folder where Dockerfile-fragment file is located. It is added to context folder
 * docker.test.image.custom.docker-module.directory.path=
 * # Name of Dockerfile-fragment file in "docker.test.image.custom.docker-module.directory.path" folder
 * # Replaces "${DOCKERFILE-FRAGMENT}" in "docker.test.image.custom.docker.dockerfile.name" Dockerfile file
 * docker.test.image.custom.docker-module.dockerfile-fragment.name=
 * # Delete custom Docker image after test execution
 * docker.test.image.custom.deleteOnExit=false
 *
 * # Mount M2 folder from host machine
 * docker.test.m2.mount.enabled=true
 * # Path of M2 folder on host machine
 * docker.test.m2.mount.path=${settings.localRepository}/..
 * # Mount path in Docker container
 * docker.test.m2.container.mount.path=/root/.m2
 *
 * # Enable debugging mode
 * docker.test.debugger.enabled=false
 * # Debugging port
 * docker.test.debugger.port=5005
 * ```
 *
 * Example Dockerfile (`docker.test.image.custom.docker.dockerfile.name`):
 * ```
 * FROM azul/zulu-openjdk-centos:11.0.5
 *
 * RUN echo "en_US.UTF-8 UTF-8" >> /etc/locale.gen && \
 * localedef --quiet -c -i en_US -f UTF-8 en_US.UTF-8
 *
 * ENV LANG en_US.UTF-8
 * ENV LANGUAGE en_US
 * ENV LC_ALL en_US.UTF-8
 *
 * ENV MAVEN_VERSION 3.6.3
 *
 * RUN yum install -y wget && \
 * # Maven
 * cd opt && \
 * wget https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz && \
 * tar xzf apache-maven-$MAVEN_VERSION-bin.tar.gz && \
 * rm -f apache-maven-$MAVEN_VERSION-bin.tar.gz
 *
 * ENV JRE_HOME /usr/lib/jvm/zulu-11
 * ENV JAVA_HOME /usr/lib/jvm/zulu-11
 * ENV M2_HOME /opt/apache-maven-$MAVEN_VERSION
 * ENV PATH ${M2_HOME}/bin:${PATH}
 *
 * RUN yum clean all && rm -rf /tmp/\* /var/tmp/\* /var/cache/yum/\*
 *
 * ```
 */
class DockerExtension : BeforeAllCallback, AfterAllCallback, InvocationInterceptor {

    private val configuration = Configuration()

    private val testContainerCoordinator = TestContainerCoordinator(
        configuration.getProperty("docker.test.image.name"),
        configuration.getProperty("docker.test.image.custom.enabled", Boolean::class.java),
        configuration.getProperty("docker.test.image.custom.name"),
        configuration.getProperty("docker.test.image.custom.docker.directory.path"),
        configuration.getProperty("docker.test.image.custom.docker.dockerfile.name"),
        configuration.getProperty("docker.test.image.custom.docker-module.directory.path"),
        configuration.getProperty("docker.test.image.custom.docker-module.dockerfile-fragment.name"),
        configuration.getProperty("docker.test.image.custom.deleteOnExit", Boolean::class.java),
        configuration.getProperty("docker.test.project.path"),
        configuration.getProperty("docker.test.project.container.path"),
        configuration.getProperty("docker.test.m2.mount.enabled", Boolean::class.java),
        configuration.getProperty("docker.test.m2.mount.path"),
        configuration.getProperty("docker.test.m2.container.mount.path"),
        configuration.getProperty("docker.test.debugger.enabled", Boolean::class.java),
        configuration.getProperty("docker.test.debugger.port", Int::class.java)
    )


    private val mavenOnTestContainerRunner = MavenOnTestContainerRunner(
        testContainerCoordinator,
        configuration.getProperty("docker.test.maven.container.test.command"),
        configuration.getProperty("docker.test.maven.container.test.run-after"),
        configuration.getProperty("docker.test.project.container.path"),
        configuration.getProperty("docker.test.debugger.enabled", Boolean::class.java),
        configuration.getProperty("docker.test.debugger.port", Int::class.java)
    )

    override fun beforeAll(context: ExtensionContext) {
        if (context.getParents().doesNotContainJupiterEngineExecutionContext()) {
            throw DockerExtensionException("DockerExtension supports only JupiterTestEngine")
        }

        runOnHost {
            testContainerCoordinator.apply {
                init()
                start()
            }
        }
    }

    private fun ExtensionContext.getParents(): List<ExtensionContext> =
        if (parent.isPresent) {
            val parent = parent.get()
            parent.getParents() + parent
        } else {
            emptyList()
        }

    private fun List<ExtensionContext>.doesNotContainJupiterEngineExecutionContext(): Boolean =
        !any { it.javaClass.canonicalName == "org.junit.jupiter.engine.descriptor.JupiterEngineExtensionContext" }


    override fun afterAll(context: ExtensionContext) {
        runOnHost {
            testContainerCoordinator.stop()
        }
    }

    override fun interceptTestMethod(
        invocation: InvocationInterceptor.Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext
    ) {
        val method = invocationContext.executable
        if (method.isSpecialKotlinName()) {
            throw DockerExtensionException("DockerExtension supports only classic Java test names (without spaces)")
        }

        runOnHost {
            mavenOnTestContainerRunner.runTest(method)
            markTestAsExecuted(invocation)
        }

        runOnDocker {
            super.interceptTestMethod(invocation, invocationContext, extensionContext)
        }
    }

    private fun Method.isSpecialKotlinName(): Boolean =
        this.name.contains(" ")

    private fun markTestAsExecuted(invocation: InvocationInterceptor.Invocation<Void>) {
        Class.forName("org.junit.jupiter.engine.execution.InvocationInterceptorChain\$ValidatingInvocation")
            .getDeclaredField("invoked")
            .also { field -> field.isAccessible = true }
            .also { field -> (field.get(invocation) as AtomicBoolean).set(true) }
            .also { field -> field.isAccessible = false }
    }

    private fun onDocker(): Boolean =
        File("/.dockerenv").exists()

    private fun runOnHost(toRun: () -> Unit) {
        if (!onDocker()) {
            toRun()
        }
    }

    private fun runOnDocker(toRun: () -> Unit) {
        if (onDocker()) {
            toRun()
        }
    }
}