package dockerruncli

import dockerrun.api.ContainerStatus
import dockerrun.api.DockerContainer
import dockerrun.api.DockerRunService
import foundation.url.resolver.UrlResolver
import foundation.url.resolver.UrlProtocol2
import foundation.url.protocol.Libp2pPeer
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

fun main(args: Array<String>) {
    DockerRunCli().use { cli ->
        cli.run(args)
    }
}

class DockerRunCli : AutoCloseable {
    private var jsonOutput = false
    private var resolver: UrlResolver? = null
    private var connection: foundation.url.resolver.sandbox.SandboxedConnection<DockerRunService>? = null

    override fun close() {
        connection?.close()
        resolver?.close()
    }

    fun run(args: Array<String>) {
        if (args.isEmpty()) {
            printUsage()
            return
        }

        val command = args[0]
        val remainingArgs = args.drop(1).toTypedArray()

        jsonOutput = remainingArgs.contains("--json")
        val filteredArgs = remainingArgs.filter { it != "--json" }.toTypedArray()

        when (command) {
            "help", "--help", "-h" -> printUsage()
            "health" -> health()
            "start" -> start(filteredArgs)
            "list" -> list()
            "show" -> show(filteredArgs)
            "pause" -> pause(filteredArgs)
            "unpause" -> unpause(filteredArgs)
            "terminate" -> terminate(filteredArgs)
            else -> {
                System.err.println("Unrecognized command: $command")
                printUsage()
                System.exit(1)
            }
        }
    }

    private fun printUsage() {
        println("""
DockerRunCli - CLI for url://dockerrun/ service

Usage: java -jar dockerrun-cli.jar <command> [options]

Commands:
  health                                    Check if the docker run service is reachable
  start <image> [--env KEY=VAL]... [--timeout SECS]  Start a new container
  list                                      List all containers
  show <uuid>                               Show container details
  pause <uuid>                              Pause a running container
  unpause <uuid>                            Unpause a paused container
  terminate <uuid>                          Terminate a container

Options:
  --json                                    Output in JSON format (for scripting)
  --env KEY=VALUE                           Set environment variable (repeatable)
  --timeout SECONDS                         Auto-terminate after this many seconds (default: 0 = never)
  --help, -h                                Show this help message

Examples:
  # Check service health
  java -jar dockerrun-cli.jar health

  # Start an nginx container with auto-termination after 1 hour
  java -jar dockerrun-cli.jar start docker.io/library/nginx:latest --env PORT=8080 --timeout 3600

  # List all containers
  java -jar dockerrun-cli.jar list

  # Show container details
  java -jar dockerrun-cli.jar show 550e8400-e29b-41d4-a716-446655440000

  # Pause a container
  java -jar dockerrun-cli.jar pause 550e8400-e29b-41d4-a716-446655440000

  # Terminate a container
  java -jar dockerrun-cli.jar terminate 550e8400-e29b-41d4-a716-446655440000

  # JSON output for scripting
  java -jar dockerrun-cli.jar list --json
        """.trimIndent())
    }

    private fun connectToService(): DockerRunService? {
        return try {
            val peerId = "12D3KooWLMyXNfwhcX1YsiNx3hnjk3GGSfsU1fydRa8bzrE6scMT"
            val multiaddr = "/ip4/198.199.106.165/tcp/35000/p2p/$peerId"

            val bootstrapPeer = Libp2pPeer.remote(
                peerId = peerId,
                multiaddresses = listOf(multiaddr),
                advertisedServices = listOf("dockerrun", "dockerimages", "tasks", "helloworld", "simpledemo")
            )

            val newResolver = UrlResolver(UrlProtocol2(bootstrapPeers = listOf(bootstrapPeer)))
            this.resolver = newResolver

            if (!jsonOutput) {
                System.err.println("Connecting to docker run service...")
            }

            val newConnection = newResolver.openSandboxedConnection("url://dockerrun/", DockerRunService::class)
            this.connection = newConnection
            newConnection.proxy
        } catch (e: Exception) {
            if (jsonOutput) {
                val error = JSONObject()
                error.put("error", true)
                error.put("message", "Failed to connect to docker run service: ${e.message}")
                println(error.toString(2))
            } else {
                System.err.println("Error: Failed to connect to docker run service at url://dockerrun/")
                System.err.println("Reason: ${e.message}")
                System.err.println()
                System.err.println("Stack trace:")
                e.printStackTrace(System.err)
                System.err.println()
                System.err.println("Make sure the DockerRunServerService is running and reachable.")
            }
            null
        }
    }

    private fun health() {
        val startTime = System.currentTimeMillis()

        try {
            val peerId = "12D3KooWLMyXNfwhcX1YsiNx3hnjk3GGSfsU1fydRa8bzrE6scMT"
            val multiaddr = "/ip4/198.199.106.165/tcp/35000/p2p/$peerId"

            val bootstrapPeer = Libp2pPeer.remote(
                peerId = peerId,
                multiaddresses = listOf(multiaddr),
                advertisedServices = listOf("dockerrun")
            )

            val newResolver = UrlResolver(UrlProtocol2(bootstrapPeers = listOf(bootstrapPeer)))
            this.resolver = newResolver

            if (!jsonOutput) {
                System.err.println("Checking docker run service health...")
            }

            val response = newResolver.openConnection("url://dockerrun/health", String::class)
            val elapsed = System.currentTimeMillis() - startTime

            if (jsonOutput) {
                val result = JSONObject()
                result.put("healthy", true)
                result.put("service", "url://dockerrun/")
                result.put("response", response)
                result.put("latencyMs", elapsed)
                println(result.toString(2))
            } else {
                println("Docker run service is healthy")
                println("  Service: url://dockerrun/")
                println("  Response: $response")
                println("  Latency: ${elapsed}ms")
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            if (jsonOutput) {
                val result = JSONObject()
                result.put("healthy", false)
                result.put("service", "url://dockerrun/")
                result.put("error", e.message)
                result.put("latencyMs", elapsed)
                println(result.toString(2))
            } else {
                System.err.println("Docker run service health check failed")
                System.err.println("  Error: ${e.message}")
                System.err.println("  Latency: ${elapsed}ms")
            }
            System.exit(1)
        }
    }

    private fun start(args: Array<String>) {
        if (args.isEmpty()) {
            System.err.println("Error: start requires a Docker image reference")
            System.err.println("Usage: dockerrun-cli start <image> [--env KEY=VAL]... [--timeout SECS]")
            System.exit(1)
            return
        }

        val service = connectToService() ?: return
        val imageReference = args[0]

        val envVars = mutableMapOf<String, String>()
        var autoTerminateSeconds = 0L

        var i = 1
        while (i < args.size) {
            when (args[i]) {
                "--env", "-e" -> {
                    if (i + 1 < args.size) {
                        val keyValue = args[i + 1]
                        val eqIdx = keyValue.indexOf('=')
                        if (eqIdx > 0) {
                            envVars[keyValue.substring(0, eqIdx)] = keyValue.substring(eqIdx + 1)
                        } else {
                            System.err.println("Error: --env value must be in KEY=VALUE format, but got: $keyValue")
                            System.exit(1)
                            return
                        }
                        i += 2
                    } else {
                        System.err.println("Error: --env requires a KEY=VALUE argument")
                        System.exit(1)
                        return
                    }
                }
                "--timeout", "-t" -> {
                    if (i + 1 < args.size) {
                        autoTerminateSeconds = args[i + 1].toLongOrNull() ?: run {
                            System.err.println("Error: --timeout requires a numeric value in seconds, but got: ${args[i + 1]}")
                            System.exit(1)
                            return
                        }
                        i += 2
                    } else {
                        System.err.println("Error: --timeout requires a numeric value in seconds")
                        System.exit(1)
                        return
                    }
                }
                else -> {
                    System.err.println("Unrecognized option: ${args[i]}")
                    printUsage()
                    System.exit(1)
                    return
                }
            }
        }

        try {
            val container = service.startContainer(imageReference, envVars, autoTerminateSeconds)

            if (jsonOutput) {
                println(containerToJson(container).toString(2))
            } else {
                println("Started container:")
                println("  UUID:           ${container.uuid}")
                println("  Image:          ${container.imageReference}")
                println("  Status:         ${container.status}")
                if (autoTerminateSeconds > 0) {
                    println("  Auto-terminate: ${autoTerminateSeconds}s")
                }
                if (envVars.isNotEmpty()) {
                    println("  Environment:")
                    for ((key, value) in envVars) {
                        println("    $key=$value")
                    }
                }
                println()
                println("Use 'show ${container.uuid}' to check container status")
            }
        } catch (e: Exception) {
            handleError("start container", e)
        }
    }

    private fun list() {
        val service = connectToService() ?: return

        try {
            val containers = service.getAllContainers().toList()

            if (!jsonOutput) {
                System.err.println("[CLI] Got ${containers.size} containers")
            }

            if (jsonOutput) {
                val arr = JSONArray()
                for (container in containers) {
                    arr.put(containerToJson(container))
                }
                println(arr.toString(2))
            } else {
                if (containers.isEmpty()) {
                    println("No containers found.")
                } else {
                    println("Containers:")
                    for (container in containers) {
                        val statusIcon = when (container.status) {
                            ContainerStatus.STARTING -> "[.]"
                            ContainerStatus.RUNNING -> "[+]"
                            ContainerStatus.PAUSED -> "[~]"
                            ContainerStatus.TERMINATED -> "[-]"
                            ContainerStatus.FAILED -> "[x]"
                        }
                        val shortUuid = container.uuid.toString().take(8)
                        val timeoutInfo = if (container.autoTerminateSeconds > 0) " (${container.autoTerminateSeconds}s)" else ""
                        println("  $statusIcon $shortUuid  ${container.imageReference}$timeoutInfo")
                    }
                    println()
                    println("${containers.size} container(s) total")
                }
            }
        } catch (e: Exception) {
            handleError("list containers", e)
        }
    }

    private fun show(args: Array<String>) {
        if (args.isEmpty()) {
            System.err.println("Error: show requires a container UUID")
            System.err.println("Usage: dockerrun-cli show <uuid>")
            System.exit(1)
            return
        }

        val service = connectToService() ?: return
        val uuidStr = args[0]

        try {
            val uuid = parseUuid(uuidStr) ?: return
            val container = service.getContainer(uuid)

            if (jsonOutput) {
                println(containerToJson(container).toString(2))
            } else {
                println("Container Details:")
                println("  UUID:              ${container.uuid}")
                println("  Image:             ${container.imageReference}")
                println("  Status:            ${container.status}")
                println("  Created:           ${formatDate(container.createdAt)}")
                if (container.autoTerminateSeconds > 0) {
                    println("  Auto-terminate:    ${container.autoTerminateSeconds}s")
                }
                container.dockerContainerId?.let { println("  Docker ID:         $it") }
                container.errorMessage?.let { println("  Error:             $it") }
                val envVars = container.environmentVariables
                if (envVars.isNotEmpty()) {
                    println("  Environment:")
                    for ((key, value) in envVars) {
                        println("    $key=$value")
                    }
                }
            }
        } catch (e: Exception) {
            handleError("show container '$uuidStr'", e)
        }
    }

    private fun pause(args: Array<String>) {
        if (args.isEmpty()) {
            System.err.println("Error: pause requires a container UUID")
            System.err.println("Usage: dockerrun-cli pause <uuid>")
            System.exit(1)
            return
        }

        val service = connectToService() ?: return
        val uuidStr = args[0]

        try {
            val uuid = parseUuid(uuidStr) ?: return
            val container = service.getContainer(uuid)
            service.pauseContainer(container)

            if (jsonOutput) {
                val result = JSONObject()
                result.put("paused", true)
                result.put("uuid", uuid.toString())
                println(result.toString(2))
            } else {
                println("Paused container $uuid")
            }
        } catch (e: Exception) {
            handleError("pause container '$uuidStr'", e)
        }
    }

    private fun unpause(args: Array<String>) {
        if (args.isEmpty()) {
            System.err.println("Error: unpause requires a container UUID")
            System.err.println("Usage: dockerrun-cli unpause <uuid>")
            System.exit(1)
            return
        }

        val service = connectToService() ?: return
        val uuidStr = args[0]

        try {
            val uuid = parseUuid(uuidStr) ?: return
            val container = service.getContainer(uuid)
            service.unpauseContainer(container)

            if (jsonOutput) {
                val result = JSONObject()
                result.put("unpaused", true)
                result.put("uuid", uuid.toString())
                println(result.toString(2))
            } else {
                println("Unpaused container $uuid")
            }
        } catch (e: Exception) {
            handleError("unpause container '$uuidStr'", e)
        }
    }

    private fun terminate(args: Array<String>) {
        if (args.isEmpty()) {
            System.err.println("Error: terminate requires a container UUID")
            System.err.println("Usage: dockerrun-cli terminate <uuid>")
            System.exit(1)
            return
        }

        val service = connectToService() ?: return
        val uuidStr = args[0]

        try {
            val uuid = parseUuid(uuidStr) ?: return
            val container = service.getContainer(uuid)
            val image = container.imageReference
            service.terminateContainer(container)

            if (jsonOutput) {
                val result = JSONObject()
                result.put("terminated", true)
                result.put("uuid", uuid.toString())
                result.put("imageReference", image)
                println(result.toString(2))
            } else {
                println("Terminated container:")
                println("  UUID:  $uuid")
                println("  Image: $image")
            }
        } catch (e: Exception) {
            handleError("terminate container '$uuidStr'", e)
        }
    }

    private fun parseUuid(uuidStr: String): UUID? {
        return try {
            UUID.fromString(uuidStr)
        } catch (e: IllegalArgumentException) {
            System.err.println("Error: Invalid UUID format: $uuidStr")
            System.err.println("Expected format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx")
            System.exit(1)
            null
        }
    }

    private fun containerToJson(container: DockerContainer): JSONObject {
        val obj = JSONObject()
        obj.put("uuid", container.uuid.toString())
        obj.put("imageReference", container.imageReference)
        obj.put("status", container.status.name)
        obj.put("createdAt", container.createdAt)
        obj.put("autoTerminateSeconds", container.autoTerminateSeconds)
        container.dockerContainerId?.let { obj.put("dockerContainerId", it) }
        container.errorMessage?.let { obj.put("errorMessage", it) }
        val envVars = container.environmentVariables
        if (envVars.isNotEmpty()) {
            val envObj = JSONObject()
            for ((key, value) in envVars) {
                envObj.put(key, value)
            }
            obj.put("environmentVariables", envObj)
        }
        return obj
    }

    private fun formatDate(epochMillis: Long): String {
        if (epochMillis == 0L) return "unknown"
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        return sdf.format(Date(epochMillis))
    }

    private fun handleError(action: String, e: Exception) {
        if (jsonOutput) {
            val error = JSONObject()
            error.put("error", true)
            error.put("action", action)
            error.put("message", e.message)
            println(error.toString(2))
        } else {
            System.err.println("Error: Failed to $action")
            System.err.println("Reason: ${e.message}")
        }
        System.exit(1)
    }
}
