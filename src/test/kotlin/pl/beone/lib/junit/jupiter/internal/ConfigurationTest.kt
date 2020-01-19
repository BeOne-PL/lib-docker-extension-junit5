package pl.beone.lib.junit.jupiter.internal

import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import org.junit.jupiter.api.Test
import java.io.IOException

class ConfigurationTest {

    @Test
    fun getProperty() {
        val configuration = Configuration()

        configuration.getProperty("docker.test.image.custom.name") shouldBe "docker-extension-junit5:test"

        shouldThrow<NoSuchElementException> {
            configuration.getProperty("absent.property")
        }.message shouldBe "There is no <absent.property> element"
    }

    @Test
    fun `getProperty with casting`() {
        val configuration = Configuration()

        configuration.getProperty("docker.test.image.custom.enabled", Boolean::class.java) shouldBe true

        shouldThrow<NoSuchElementException> {
            configuration.getProperty("absent.property", Boolean::class.java)
        }.message shouldBe "There is no <absent.property> element"
    }

    @Test
    fun `getProperty with placeholder`() {
        val configuration = Configuration()

        configuration.getProperty("docker.test.maven.container.test.run-after") shouldBe "chown -R 1000:1000 /test"
    }

    private fun getRootResource(): String =
        Configuration::class.java.classLoader?.getResource(".")?.path ?: throw IOException("Couldn't get resource root path (.)")
}