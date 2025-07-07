package org.example

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.Path.Companion.toOkioPath

fun DockerImage(tarballFile: java.io.File): DockerImage {
    return DockerImage(FileSystem.SYSTEM, tarballFile.toOkioPath())
}

class DockerImage(private val fileSystem: FileSystem, private val path: Path) {
    val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    private var manifestNode: JsonNode? = null
    private var configNode: JsonNode? = null
    private val fileContents = mutableMapOf<String, ByteArray>()

    init {
        readMetadata()
    }

    private fun readMetadata() {
        if (!fileSystem.exists(path)) {
            throw IllegalArgumentException("Tarball not found at $path")
        }

        fileSystem.source(path).buffer().inputStream().use { bufferedInputStream ->
            val input = if (path.name.endsWith(".gz")) {
                GzipCompressorInputStream(bufferedInputStream)
            } else {
                bufferedInputStream
            }
            TarArchiveInputStream(input).use { tarInput ->
                var entry = tarInput.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                    val content = tarInput.readBytes()
                    fileContents[entry.name] = content
                    if (entry.name == "manifest.json") {
                        manifestNode = objectMapper.readTree(content.toString(Charsets.UTF_8))
                    }
                }
                entry = tarInput.nextEntry
            }
          } // Closing brace for TarArchiveInputStream.use
        } // Closing brace for fileSystem.source.buffer.inputStream.use

        manifestNode?.let {
            manifest ->
            if (manifest.isArray && manifest.size() > 0) {
                val configPath = manifest[0]?.get("Config")?.asText()
                if (configPath != null) {
                    val configContent = fileContents[configPath]
                    if (configContent != null) {
                        configNode = objectMapper.readTree(configContent.toString(Charsets.UTF_8))
                    } else {
                        throw IllegalStateException("Image configuration file '$configPath' not found in the tarball.")
                    }
                } else {
                    throw IllegalStateException("'Config' field not found in manifest.json.")
                }
            } else {
                throw IllegalStateException("Invalid manifest.json format.")
            }
        } ?: throw IllegalStateException("manifest.json not found in the tarball.")
    }

    fun getManifest(): JsonNode? = manifestNode

    fun getConfig(): JsonNode? = configNode

    fun getTags(): List<String> {
        val tags = mutableListOf<String>()
        manifestNode?.let {
            manifest ->
            if (manifest.isArray && manifest.size() > 0) {
                val repoTagsNode = manifest[0]?.get("RepoTags")
                if (repoTagsNode != null && repoTagsNode.isArray) {
                    repoTagsNode.forEach { tag ->
                        tags.add(tag.asText())
                    }
                }
            }
        }
        return tags
    }

    fun getLayers(): List<String> {
        val layers = mutableListOf<String>()
        manifestNode?.let { manifest ->
            if (manifest.isArray && manifest.size() > 0) {
                val layersNode = manifest[0]?.get("Layers")
                if (layersNode != null && layersNode.isArray) {
                    layersNode.forEach { layer ->
                        layers.add(layer.asText())
                    }
                }
            }
        }
        return layers
    }

    fun setTags(newTags: List<String>) {
        manifestNode?.let {
            manifest ->
            if (manifest.isArray && manifest.size() > 0) {
                val newRepoTagsNode = objectMapper.createArrayNode()
                newTags.forEach { newRepoTagsNode.add(objectMapper.getNodeFactory().textNode(it)) }
                (manifest[0] as com.fasterxml.jackson.databind.node.ObjectNode).replace("RepoTags", newRepoTagsNode)

                // Write updated manifest.json back to the tarball
                val updatedManifestContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifestNode)
                fileContents["manifest.json"] = updatedManifestContent

                // Create a temporary path for the new tarball
                val tempPath = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "${path.name}.tmp"

                fileSystem.sink(tempPath).buffer().outputStream().use { bufferedOutputStream ->
                    val output = if (path.name.endsWith(".gz")) {
                        GzipCompressorOutputStream(bufferedOutputStream)
                    } else {
                        bufferedOutputStream
                    }
                    TarArchiveOutputStream(output).use { tarOutput ->
                        for ((name, content) in fileContents) {
                            val entry = TarArchiveEntry(name)
                            entry.size = content.size.toLong()
                            tarOutput.putArchiveEntry(entry)
                            tarOutput.write(content)
                            tarOutput.closeArchiveEntry()
                        }
                    }
                }
                // Replace the original tarball with the new one
                try {
                    fileSystem.atomicMove(tempPath, path)
                } catch (e: Exception) {
                    // Fallback if atomicMove is not supported or fails
                    fileSystem.copy(tempPath, path)
                    fileSystem.delete(tempPath)
                }
            } else {
                throw IllegalStateException("Invalid manifest.json format.")
            }
        } ?: throw IllegalStateException("manifest.json not found.")
    }
}
