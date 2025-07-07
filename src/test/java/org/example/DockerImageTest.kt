package org.example

import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.buffer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DockerImageTest {

    @TempDir
    lateinit var tempDir: File
    private lateinit var testTarballFile: File
    private val fileSystem = FileSystem.SYSTEM

    @BeforeEach
    fun setUp() {
        testTarballFile = File(tempDir, "test-image.tar")

        val manifestContent = """
            [
                {
                    "Config": "config.json",
                    "RepoTags": ["test-image:latest", "test-image:1.0"],
                    "Layers": ["layer1.tar.gz"]
                }
            ]
        """.trimIndent()

        val configContent = """
            {
                "architecture": "amd64",
                "os": "linux"
            }
        """.trimIndent()

        val layerContent = "This is dummy layer content."

        val testTarballPath = testTarballFile.toOkioPath()
        fileSystem.sink(testTarballPath).buffer().outputStream().use { outputStream ->
            org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(outputStream).use { tarOutput ->
                var entry = org.apache.commons.compress.archivers.tar.TarArchiveEntry("manifest.json")
                var bytes = manifestContent.toByteArray(Charsets.UTF_8)
                entry.size = bytes.size.toLong()
                tarOutput.putArchiveEntry(entry)
                tarOutput.write(bytes)
                tarOutput.closeArchiveEntry()

                entry = org.apache.commons.compress.archivers.tar.TarArchiveEntry("config.json")
                bytes = configContent.toByteArray(Charsets.UTF_8)
                entry.size = bytes.size.toLong()
                tarOutput.putArchiveEntry(entry)
                tarOutput.write(bytes)
                tarOutput.closeArchiveEntry()

                entry = org.apache.commons.compress.archivers.tar.TarArchiveEntry("layer1.tar.gz")
                bytes = layerContent.toByteArray(Charsets.UTF_8)
                entry.size = bytes.size.toLong()
                tarOutput.putArchiveEntry(entry)
                tarOutput.write(bytes)
                tarOutput.closeArchiveEntry()
            }
        }
    }

    @Test
    fun `test DockerImage constructor with FileSystem and Path`() {
        val image = DockerImage(fileSystem, testTarballFile.toOkioPath())
        assertNotNull(image.getManifest(), "Manifest should not be null")
        assertNotNull(image.getConfig(), "Config should not be null")
    }

    @Test
    fun `test top-level DockerImage function with java File`() {
        val image = DockerImage(testTarballFile)
        assertNotNull(image.getManifest(), "Manifest should not be null")
        assertNotNull(image.getConfig(), "Config should not be null")
    }

    @Test
    fun `getManifest returns correct manifest content`() {
        val image = DockerImage(testTarballFile)
        val manifest = image.getManifest()
        assertNotNull(manifest)
        assertTrue(manifest!!.isArray)
        assertEquals(1, manifest.size())
        assertEquals("config.json", manifest[0]?.get("Config")?.asText())
    }

    @Test
    fun `getConfig returns correct config content`() {
        val image = DockerImage(testTarballFile)
        val config = image.getConfig()
        assertNotNull(config)
        assertEquals("amd64", config?.get("architecture")?.asText())
        assertEquals("linux", config?.get("os")?.asText())
    }

    @Test
    fun `getTags returns correct tags`() {
        val image = DockerImage(testTarballFile)
        val tags = image.getTags()
        assertEquals(listOf("test-image:latest", "test-image:1.0"), tags)
    }

    @Test
    fun `getLayers returns correct layers`() {
        val image = DockerImage(testTarballFile)
        val layers = image.getLayers()
        assertEquals(listOf("layer1.tar.gz"), layers)
    }

    @Test
    fun `setTags updates tags in the tarball`() {
        val image = DockerImage(testTarballFile)
        val newTags = listOf("new-image:v1", "new-image:latest")
        image.setTags(newTags)

        val updatedImage = DockerImage(testTarballFile)
        assertEquals(newTags, updatedImage.getTags())

        val manifest = updatedImage.getManifest()
        assertNotNull(manifest)
        val repoTagsNode = manifest!![0]?.get("RepoTags")
        assertNotNull(repoTagsNode)
        assertTrue(repoTagsNode!!.isArray)
        val tagsList = repoTagsNode.map { it.asText() }
        assertEquals(newTags, tagsList)
    }

    @Test
    fun `constructor throws IllegalArgumentException for non-existent tarball`() {
        val nonExistentFile = File(tempDir, "nonexistent.tar")
        // Ensure the file does not exist before testing
        if (nonExistentFile.exists()) {
            nonExistentFile.delete()
        }
        assertThrows(IllegalArgumentException::class.java) {
            DockerImage(nonExistentFile)
        }
    }
}
