package org.example

import org.example.DockerImage
import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: java -jar <your-jar-name>.jar <path-to-docker-image-tarball>")
        return
    }

    val tarballPath = args[0]
    val tarFile = File(tarballPath)

    if (!tarFile.exists()) {
        println("Error: Tarball not found at $tarballPath")
        return
    }

    try {
        // Updated to use the new top-level DockerImage function
        val editor = DockerImage(tarFile)

        println("\n--- Docker Image Configuration ---")
        editor.getConfig()?.let { config ->
            println(editor.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config))
        } ?: println("No configuration found.")

        println("\n--- Repository Tags ---")
        val tags = editor.getTags()
        if (tags.isNotEmpty()) {
            tags.forEach { tag ->
                println("- $tag")
            }
        } else {
            println("No tags found.")
        }

        println("\n--- Docker Image Manifest ---")
        editor.getManifest()?.let { manifest ->
            println(editor.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest))
        } ?: println("No manifest found.")

        println("\n--- Docker Image Layers ---")
        val layers = editor.getLayers()
        if (layers.isNotEmpty()) {
            layers.forEach { layer ->
                println("- $layer")
            }
        } else {
            println("No layers found.")
        }
    } catch (e: Exception) {
        println("An error occurred: ${e.message}")
        e.printStackTrace()
    }
}