package com.zhousl.aether.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.zhousl.aether.termux.TermuxBashTool
import com.zhousl.aether.termux.TermuxContract
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.nio.file.Paths
import java.util.Base64
import org.json.JSONObject

private const val MaxWorkspaceImportBytes = 20 * 1024 * 1024
private const val MaxWorkspaceDownloadBytes = 32 * 1024 * 1024
private const val WorkspaceDownloadChunkBytes = 512 * 1024
private const val WorkspaceUploadChunkChars = 64 * 1024
private const val WorkspaceBaseDirectoryName = ".aether/workspaces"

class WorkspaceFileBridge(
    private val context: Context,
    private val bashTool: TermuxBashTool = TermuxBashTool(context),
) {
    fun workspaceDirectory(sessionId: String): String =
        "${TermuxContract.HomeDirectory}/$WorkspaceBaseDirectoryName/$sessionId"

    suspend fun importAttachmentToWorkspace(
        sourceUri: Uri,
        sessionId: String,
        attachmentId: String,
        displayName: String,
    ): Result<ImportedWorkspaceFile> = runCatching {
        val safeFileName = buildWorkspaceFileName(
            attachmentId = attachmentId,
            displayName = displayName,
        )
        val destinationPath = "${workspaceDirectory(sessionId)}/uploads/$safeFileName"
        val bytes = readContentBytes(
            sourceUri = sourceUri,
            byteLimit = MaxWorkspaceImportBytes + 1,
        ) ?: error("Couldn't read the selected file.")

        if (bytes.size > MaxWorkspaceImportBytes) {
            error("File is larger than ${formatBytes(MaxWorkspaceImportBytes.toLong())}.")
        }

        writeWorkspaceBytes(
            absolutePath = destinationPath,
            bytes = bytes,
        ).getOrThrow()

        ImportedWorkspaceFile(
            absolutePath = destinationPath,
            bytesCopied = bytes.size.toLong(),
        )
    }

    suspend fun readWorkspaceFile(
        path: String,
        workingDirectory: String = TermuxContract.HomeDirectory,
        byteLimit: Int,
    ): Result<WorkspaceFilePayload> = runCatching {
        require(byteLimit > 0) { "byteLimit must be greater than 0." }

        val absolutePath = resolveTermuxPath(
            path = path,
            workingDirectory = workingDirectory,
        )
        val command = buildReadWorkspaceFileCommand(
            absolutePath = absolutePath,
            byteLimit = byteLimit,
        )
        val rawResult = JSONObject(bashTool.executeCommand(command))
        ensureBashSuccess(
            rawResult = rawResult,
            fallbackMessage = "Couldn't read $absolutePath from the workspace.",
        )
        val values = parseStructuredStdout(rawResult.optString("stdout"))
        val contentBase64 = values["content_b64"].orEmpty()
        val sizeBytes = values["size_bytes"]?.toLongOrNull() ?: 0L
        val bytes = if (contentBase64.isBlank()) {
            ByteArray(0)
        } else {
            Base64.getDecoder().decode(contentBase64)
        }

        WorkspaceFilePayload(
            absolutePath = absolutePath,
            bytes = bytes,
            sizeBytes = sizeBytes,
        )
    }

    suspend fun saveWorkspaceFileToDocument(
        path: String,
        destinationUri: Uri,
        byteLimit: Int = MaxWorkspaceDownloadBytes,
    ): Boolean = runCatching {
        require(byteLimit > 0) { "byteLimit must be greater than 0." }

        val absolutePath = resolveTermuxPath(path = path)
        val resolver = context.contentResolver
        val didWrite = resolver.openOutputStream(destinationUri, "w")?.use { output ->
            var expectedSizeBytes: Long? = null
            var bytesWritten = 0L

            while (expectedSizeBytes == null || bytesWritten < expectedSizeBytes) {
                val remainingBudget = (byteLimit.toLong() - bytesWritten).coerceAtLeast(0L)
                val chunk = readWorkspaceFileChunk(
                    absolutePath = absolutePath,
                    offsetBytes = bytesWritten,
                    byteLimit = minOf(
                        WorkspaceDownloadChunkBytes,
                        remainingBudget.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1),
                    ),
                    maxTotalBytes = byteLimit,
                ).getOrThrow()

                expectedSizeBytes = chunk.sizeBytes
                if (expectedSizeBytes == 0L) {
                    break
                }
                if (chunk.bytes.isEmpty()) {
                    error("Couldn't read file data from $absolutePath.")
                }

                output.write(chunk.bytes)
                output.flush()
                bytesWritten += chunk.bytes.size
            }

            true
        } ?: false
        if (!didWrite) {
            runCatching { resolver.delete(destinationUri, null, null) }
        }
        didWrite
    }.getOrDefault(false)

    fun resolveWorkspaceDownloadName(rawLink: String): String {
        val normalizedPath = resolveLinkPath(rawLink)
        return normalizedPath.substringAfterLast('/').ifBlank { "download" }
    }

    fun resolveLinkPath(rawLink: String): String =
        resolveTermuxPath(
            path = normalizeFileLink(rawLink),
            workingDirectory = TermuxContract.HomeDirectory,
        )

    fun resolveTermuxPath(
        path: String,
        workingDirectory: String = TermuxContract.HomeDirectory,
    ): String {
        val trimmedPath = path.trim()
        val normalizedWorkingDirectory = normalizeHomePrefix(workingDirectory.trim())
            .ifBlank { TermuxContract.HomeDirectory }

        return when {
            trimmedPath.isBlank() -> normalizedWorkingDirectory
            trimmedPath == "~" -> TermuxContract.HomeDirectory
            trimmedPath.startsWith("~/") -> TermuxContract.HomeDirectory + trimmedPath.removePrefix("~")
            trimmedPath.startsWith("/") -> Paths.get(trimmedPath).normalize().toString()
            else -> Paths.get(normalizedWorkingDirectory).resolve(trimmedPath).normalize().toString()
        }
    }

    fun guessMimeType(path: String): String {
        val extension = path.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension).orEmpty()
    }

    suspend fun writeWorkspaceBytes(
        absolutePath: String,
        bytes: ByteArray,
    ): Result<Long> = runCatching {
        val pathBase64 = encodeBase64(absolutePath)
        executeUploadCommand(
            command = buildInitWorkspaceUploadCommand(pathBase64),
            fallbackMessage = "Couldn't prepare $absolutePath in the workspace.",
        )

        val contentBase64 = Base64.getEncoder().encodeToString(bytes)
        contentBase64.chunked(WorkspaceUploadChunkChars).forEach { chunk ->
            executeUploadCommand(
                command = buildAppendWorkspaceUploadChunkCommand(
                    pathBase64 = pathBase64,
                    chunk = chunk,
                ),
                fallbackMessage = "Couldn't append a file chunk for $absolutePath in the workspace.",
            )
        }

        val rawResult = executeUploadCommand(
            command = buildFinalizeWorkspaceUploadCommand(pathBase64),
            fallbackMessage = "Couldn't finalize $absolutePath in the workspace.",
        )
        val values = parseStructuredStdout(rawResult.optString("stdout"))
        values["bytes_written"]?.toLongOrNull() ?: bytes.size.toLong()
    }

    private suspend fun executeUploadCommand(
        command: String,
        fallbackMessage: String,
    ): JSONObject {
        val rawResult = JSONObject(bashTool.executeCommand(command))
        ensureBashSuccess(
            rawResult = rawResult,
            fallbackMessage = fallbackMessage,
        )
        return rawResult
    }

    private fun buildWorkspaceFileName(
        attachmentId: String,
        displayName: String,
    ): String {
        val trimmedName = displayName.trim().ifBlank { "attachment" }
        val dotIndex = trimmedName.lastIndexOf('.')
        val stem = if (dotIndex > 0) trimmedName.substring(0, dotIndex) else trimmedName
        val extension = if (dotIndex > 0 && dotIndex < trimmedName.lastIndex) {
            trimmedName.substring(dotIndex)
        } else {
            ""
        }
        val safeStem = sanitizePathSegment(stem).ifBlank { "attachment" }
        val safeExtension = sanitizeExtension(extension)
        val suffix = attachmentId.substringAfter("attachment-", attachmentId)
            .replace(Regex("[^a-zA-Z0-9_-]"), "")
            .takeLast(12)
            .ifBlank { System.currentTimeMillis().toString() }
        return "${safeStem}_$suffix$safeExtension"
    }

    private fun sanitizePathSegment(value: String): String =
        value.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(80)

    private fun sanitizeExtension(value: String): String {
        if (!value.startsWith('.')) return ""
        val sanitized = value.drop(1).replace(Regex("[^a-zA-Z0-9]"), "").take(12)
        return if (sanitized.isBlank()) "" else ".$sanitized"
    }

    private fun normalizeFileLink(rawLink: String): String {
        val trimmed = rawLink.trim()
        if (trimmed.isBlank()) return ""

        val withoutScheme = if (trimmed.startsWith("file://", ignoreCase = true)) {
            trimmed.substring("file://".length)
        } else {
            trimmed
        }
        val withoutLocalhost = when {
            withoutScheme.startsWith("localhost/") -> "/" + withoutScheme.removePrefix("localhost/")
            withoutScheme == "localhost" -> "/"
            else -> withoutScheme
        }
        val decodedPath = URLDecoder.decode(withoutLocalhost, Charsets.UTF_8.name()).trim()
        return decodedPath.replace(Regex(":(\\d+)$"), "")
    }

    private fun normalizeHomePrefix(path: String): String = when {
        path == "~" -> TermuxContract.HomeDirectory
        path.startsWith("~/") -> TermuxContract.HomeDirectory + path.removePrefix("~")
        else -> path
    }

    private fun parseStructuredStdout(stdout: String): Map<String, String> = buildMap {
        stdout.lineSequence()
            .filter { it.isNotBlank() }
            .forEach { line ->
                val separatorIndex = line.indexOf('=')
                if (separatorIndex <= 0) return@forEach
                put(
                    line.substring(0, separatorIndex),
                    line.substring(separatorIndex + 1),
                )
            }
    }

    private fun ensureBashSuccess(
        rawResult: JSONObject,
        fallbackMessage: String,
    ) {
        if (rawResult.optBoolean("ok")) return

        error(
            rawResult.optString("errmsg")
                .ifBlank { rawResult.optString("stderr") }
                .ifBlank { rawResult.optString("hint") }
                .ifBlank { fallbackMessage }
        )
    }

    private fun buildReadWorkspaceFileCommand(
        absolutePath: String,
        byteLimit: Int,
    ): String = buildString {
        appendCommonShellPreamble(this)
        appendLine("path=\"\$(decode_b64 '${encodeBase64(absolutePath)}')\"")
        appendLine("byte_limit=$byteLimit")
        appendLine("if [ ! -e \"\$path\" ]; then")
        appendLine("  printf 'File not found: %s\\n' \"\$path\" >&2")
        appendLine("  exit 20")
        appendLine("fi")
        appendLine("if [ ! -f \"\$path\" ]; then")
        appendLine("  printf 'Path is not a file: %s\\n' \"\$path\" >&2")
        appendLine("  exit 21")
        appendLine("fi")
        appendLine("size_bytes=\$(wc -c < \"\$path\" | tr -d '[:space:]')")
        appendLine("if [ \"\$size_bytes\" -gt \"\$byte_limit\" ]; then")
        appendLine("  printf 'File is larger than %s bytes: %s\\n' \"\$byte_limit\" \"\$path\" >&2")
        appendLine("  exit 22")
        appendLine("fi")
        appendLine("content_b64=\$(base64 < \"\$path\" | tr -d '\\n')")
        appendLine("emit_kv size_bytes \"\$size_bytes\"")
        appendLine("emit_kv content_b64 \"\$content_b64\"")
    }

    private suspend fun readWorkspaceFileChunk(
        absolutePath: String,
        offsetBytes: Long,
        byteLimit: Int,
        maxTotalBytes: Int,
    ): Result<WorkspaceFilePayload> = runCatching {
        require(offsetBytes >= 0L) { "offsetBytes must not be negative." }
        require(byteLimit > 0) { "byteLimit must be greater than 0." }
        require(maxTotalBytes > 0) { "maxTotalBytes must be greater than 0." }

        val command = buildReadWorkspaceFileChunkCommand(
            absolutePath = absolutePath,
            offsetBytes = offsetBytes,
            byteLimit = byteLimit,
            maxTotalBytes = maxTotalBytes,
        )
        val rawResult = JSONObject(bashTool.executeCommand(command))
        ensureBashSuccess(
            rawResult = rawResult,
            fallbackMessage = "Couldn't read $absolutePath from the workspace.",
        )

        val values = parseStructuredStdout(rawResult.optString("stdout"))
        val sizeBytes = values["size_bytes"]?.toLongOrNull()
            ?: error("Workspace read didn't report the file size.")
        val chunkSize = values["chunk_size"]?.toIntOrNull() ?: 0
        val contentBase64 = values["content_b64"].orEmpty()
        val bytes = if (contentBase64.isBlank()) {
            ByteArray(0)
        } else {
            Base64.getDecoder().decode(contentBase64)
        }

        if (chunkSize > 0 && bytes.isEmpty()) {
            error("Workspace read returned an empty data chunk.")
        }
        if (chunkSize != bytes.size) {
            error("Workspace read returned ${bytes.size} bytes, expected $chunkSize.")
        }

        WorkspaceFilePayload(
            absolutePath = absolutePath,
            bytes = bytes,
            sizeBytes = sizeBytes,
        )
    }

    private fun buildReadWorkspaceFileChunkCommand(
        absolutePath: String,
        offsetBytes: Long,
        byteLimit: Int,
        maxTotalBytes: Int,
    ): String = buildString {
        appendCommonShellPreamble(this)
        appendLine("path=\"\$(decode_b64 '${encodeBase64(absolutePath)}')\"")
        appendLine("offset_bytes=$offsetBytes")
        appendLine("chunk_limit=$byteLimit")
        appendLine("max_total_bytes=$maxTotalBytes")
        appendLine("if [ ! -e \"\$path\" ]; then")
        appendLine("  printf 'File not found: %s\\n' \"\$path\" >&2")
        appendLine("  exit 20")
        appendLine("fi")
        appendLine("if [ ! -f \"\$path\" ]; then")
        appendLine("  printf 'Path is not a file: %s\\n' \"\$path\" >&2")
        appendLine("  exit 21")
        appendLine("fi")
        appendLine("size_bytes=\$(wc -c < \"\$path\" | tr -d '[:space:]')")
        appendLine("if [ \"\$size_bytes\" -gt \"\$max_total_bytes\" ]; then")
        appendLine("  printf 'File is larger than %s bytes: %s\\n' \"\$max_total_bytes\" \"\$path\" >&2")
        appendLine("  exit 22")
        appendLine("fi")
        appendLine("chunk_size=0")
        appendLine("content_b64=''")
        appendLine("if [ \"\$offset_bytes\" -lt \"\$size_bytes\" ]; then")
        appendLine("  remaining=\$((size_bytes - offset_bytes))")
        appendLine("  chunk_size=\$chunk_limit")
        appendLine("  if [ \"\$remaining\" -lt \"\$chunk_size\" ]; then")
        appendLine("    chunk_size=\$remaining")
        appendLine("  fi")
        appendLine("  content_b64=\$(tail -c \"+\$((offset_bytes + 1))\" -- \"\$path\" | head -c \"\$chunk_size\" | base64 | tr -d '\\n')")
        appendLine("fi")
        appendLine("emit_kv size_bytes \"\$size_bytes\"")
        appendLine("emit_kv chunk_size \"\$chunk_size\"")
        appendLine("emit_kv content_b64 \"\$content_b64\"")
    }

    private fun buildInitWorkspaceUploadCommand(
        pathBase64: String,
    ): String = buildString {
        appendCommonShellPreamble(this)
        appendLine("path=\"\$(decode_b64 '$pathBase64')\"")
        appendLine("parent_dir=\$(dirname -- \"\$path\")")
        appendLine("mkdir -p \"\$parent_dir\"")
        appendLine("tmp_b64=\"\${path}.aether-upload.b64\"")
        appendLine("tmp_path=\"\${path}.aether-tmp\"")
        appendLine("rm -f \"\$tmp_b64\" \"\$tmp_path\"")
        appendLine(": > \"\$tmp_b64\"")
        appendLine("emit_kv stage initialized")
    }

    private fun buildAppendWorkspaceUploadChunkCommand(
        pathBase64: String,
        chunk: String,
    ): String = buildString {
        appendCommonShellPreamble(this)
        appendLine("path=\"\$(decode_b64 '$pathBase64')\"")
        appendLine("tmp_b64=\"\${path}.aether-upload.b64\"")
        appendLine("printf '%s' '$chunk' >> \"\$tmp_b64\"")
        appendLine("emit_kv stage appended")
    }

    private fun buildFinalizeWorkspaceUploadCommand(
        pathBase64: String,
    ): String = buildString {
        appendCommonShellPreamble(this)
        appendLine("path=\"\$(decode_b64 '$pathBase64')\"")
        appendLine("tmp_b64=\"\${path}.aether-upload.b64\"")
        appendLine("tmp_path=\"\${path}.aether-tmp\"")
        appendLine("trap 'rm -f \"\$tmp_path\" \"\$tmp_b64\"' EXIT")
        appendLine("base64 -d < \"\$tmp_b64\" > \"\$tmp_path\"")
        appendLine("mv \"\$tmp_path\" \"\$path\"")
        appendLine("bytes_written=\$(wc -c < \"\$path\" | tr -d '[:space:]')")
        appendLine("rm -f \"\$tmp_b64\"")
        appendLine("trap - EXIT")
        appendLine("emit_kv bytes_written \"\$bytes_written\"")
    }

    private fun readContentBytes(
        sourceUri: Uri,
        byteLimit: Int,
    ): ByteArray? = runCatching {
        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var totalRead = 0

            while (true) {
                val read = inputStream.read(buffer)
                if (read <= 0) break

                val remaining = byteLimit - totalRead
                if (remaining <= 0) break

                val writeCount = minOf(read, remaining)
                output.write(buffer, 0, writeCount)
                totalRead += writeCount

                if (totalRead >= byteLimit) break
            }

            output.toByteArray()
        }
    }.getOrNull()

    private fun encodeBase64(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun appendCommonShellPreamble(builder: StringBuilder) {
        builder.appendLine("set -euo pipefail")
        builder.appendLine("decode_b64() {")
        builder.appendLine("  printf '%s' \"\$1\" | base64 -d")
        builder.appendLine("}")
        builder.appendLine("emit_kv() {")
        builder.appendLine("  printf '%s=%s\\n' \"\$1\" \"\$2\"")
        builder.appendLine("}")
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024f)
        else -> "$bytes B"
    }
}

data class ImportedWorkspaceFile(
    val absolutePath: String,
    val bytesCopied: Long,
)

data class WorkspaceFilePayload(
    val absolutePath: String,
    val bytes: ByteArray,
    val sizeBytes: Long,
)
