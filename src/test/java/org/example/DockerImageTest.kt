package org.example

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import java.io.ByteArrayInputStream

class DockerImageTest {

    private val TEST_TARBALL_PATH = "./test-image.tar"
    private val TEST_TARBALL_PATH_NEW = "./test-image-new.tar"
    private val ORIGINAL_MANIFEST_CONTENT = """
        [
          {
            "Config": "config.json",
            "RepoTags": [
              "test/image:latest",
              "test/image:1.0"
            ],
            "Layers": [
              "layer1.tar",
              "layer2.tar"
            ]
          }
        ]
    """.trimIndent()
    private val ORIGINAL_CONFIG_CONTENT = """
        {
          "architecture": "amd64",
          "os": "linux"
        }
    """.trimIndent()

    @BeforeEach
    fun setup() {
        // Create a dummy tarball for testing
        createDummyTarball(TEST_TARBALL_PATH, ORIGINAL_MANIFEST_CONTENT, ORIGINAL_CONFIG_CONTENT)
    }

    @AfterEach
    fun teardown() {
        // Clean up the dummy tarball
        File(TEST_TARBALL_PATH).delete()
        File(TEST_TARBALL_PATH_NEW).delete()
    }

    private fun createDummyTarball(tarballPath: String, manifestContent: String, configContent: String, layerContent: ByteArray? = null) {
        TarArchiveOutputStream(FileOutputStream(tarballPath)).use { tarOutput ->
            // Add manifest.json
            val manifestEntry = TarArchiveEntry("manifest.json")
            val manifestBytes = manifestContent.toByteArray(Charsets.UTF_8)
            manifestEntry.size = manifestBytes.size.toLong()
            tarOutput.putArchiveEntry(manifestEntry)
            tarOutput.write(manifestBytes)
            tarOutput.closeArchiveEntry()

            // Add config.json
            val configEntry = TarArchiveEntry("config.json")
            val configBytes = configContent.toByteArray(Charsets.UTF_8)
            configEntry.size = configBytes.size.toLong()
            tarOutput.putArchiveEntry(configEntry)
            tarOutput.write(configBytes)
            tarOutput.closeArchiveEntry()

            // Add layer.tar if provided
            if (layerContent != null) {
                val layerEntry = TarArchiveEntry("layer.tar")
                layerEntry.size = layerContent.size.toLong()
                tarOutput.putArchiveEntry(layerEntry)
                tarOutput.write(layerContent)
                tarOutput.closeArchiveEntry()
            }
        }
    }

    @Test
    fun testGetManifest() {
        val editor = DockerImage(TEST_TARBALL_PATH)
        val manifest = editor.getManifest()
        assertNotNull(manifest)
        assertTrue(manifest!!.isArray)
        assertEquals(1, manifest.size())
        assertEquals("config.json", manifest[0]["Config"].asText())
    }

    @Test
    fun testGetConfig() {
        val editor = DockerImage(TEST_TARBALL_PATH)
        val config = editor.getConfig()
        assertNotNull(config)
        assertEquals("amd64", config!!["architecture"].asText())
        assertEquals("linux", config["os"].asText())
    }

    @Test
    fun testGetTags() {
        val editor = DockerImage(TEST_TARBALL_PATH)
        val tags = editor.getTags()
        assertEquals(2, tags.size)
        assertTrue(tags.contains("test/image:latest"))
        assertTrue(tags.contains("test/image:1.0"))
    }

    @Test
    fun testGetLayers() {
        val editor = DockerImage(TEST_TARBALL_PATH)
        val layers = editor.getLayers()
        assertEquals(2, layers.size)
        assertTrue(layers.contains("layer1.tar"))
        assertTrue(layers.contains("layer2.tar"))
    }

    @Test
    fun testSetTags() {
        val editor = DockerImage(TEST_TARBALL_PATH)
        val newTags = listOf("new/image:2.0", "new/image:beta")
        editor.setTags(newTags)

        // Re-read the tarball to verify changes
        val updatedEditor = DockerImage(TEST_TARBALL_PATH)
        val updatedTags = updatedEditor.getTags()
        assertEquals(2, updatedTags.size)
        assertTrue(updatedTags.contains("new/image:2.0"))
        assertTrue(updatedTags.contains("new/image:beta"))
    }

    @Test
    fun testReadImageTagFromTarFile() {
        val expectedTag = "my-test-image:1.0"
        val manifestContent = """
            [
              {
                "Config": "config.json",
                "RepoTags": [
                  "$expectedTag"
                ],
                "Layers": [
                  "layer.tar"
                ]
              }
            ]
        """.trimIndent()
        val configContent = """
            {
              "architecture": "amd64",
              "os": "linux"
            }
        """.trimIndent()
        val layerContent = "This is a dummy layer".toByteArray(Charsets.UTF_8)

        createDummyTarball(TEST_TARBALL_PATH_NEW, manifestContent, configContent, layerContent)

        val editor = DockerImage(TEST_TARBALL_PATH_NEW)
        val tags = editor.getTags()

        assertEquals(1, tags.size)
        assertEquals(expectedTag, tags[0])
    }
}
