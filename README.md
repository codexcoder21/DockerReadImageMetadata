# Docker Image Metadata Reader

This Kotlin application reads a Docker image tarball and extracts its metadata, printing it to standard output.

## How to Build

To build the application, navigate to the project's root directory and run the following Gradle command:

```bash
./gradlew build
```

This will compile the source code and create a runnable JAR file in the `build/libs` directory.

## How to Run

To run the application, you need to provide the absolute path to the Docker image tarball as a command-line argument. From the project's root directory, execute:

```bash
./gradlew run --args="/path/to/your/docker-image.tar"
```

Replace `/path/to/your/docker-image.tar` with the actual absolute path to your Docker image tarball (e.g., `/root/hello-world-image.tar`).

The application will then parse the tarball and print the extracted Docker image metadata to your console.