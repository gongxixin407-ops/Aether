package com.zhousl.aether.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

private const val SkillStorageDirectoryName = "agent-skills"
private const val SkillTempDirectoryName = "agent-skills-tmp"
private const val SkillFileName = "SKILL.md"

class AgentSkillManager(
    private val context: Context,
    private val extensionsRepository: AgentExtensionsRepository,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun installSkillFromDirectory(
        treeUri: Uri,
        label: String = treeUri.toString(),
    ): Result<InstalledSkill> = withContext(Dispatchers.IO) {
        runCatching {
            val workingDirectory = createTempDirectory()
            try {
                val root = copyDocumentTree(treeUri, workingDirectory)
                val skillRoot = locateSkillRoot(root = root)
                installParsedSkill(
                    sourceRoot = skillRoot,
                    source = SkillInstallSource(
                        kind = SkillInstallKind.DocumentTree,
                        label = label,
                        uri = treeUri.toString(),
                    ),
                )
            } finally {
                workingDirectory.deleteRecursively()
            }
        }
    }

    suspend fun installSkillFromZipUri(
        zipUri: Uri,
        label: String = zipUri.toString(),
    ): Result<InstalledSkill> = withContext(Dispatchers.IO) {
        runCatching {
            val workingDirectory = createTempDirectory()
            try {
                context.contentResolver.openInputStream(zipUri)?.use { input ->
                    unzipIntoDirectory(input.readBytes(), workingDirectory)
                } ?: error("Couldn't open the selected zip file.")
                val skillRoot = locateSkillRoot(root = workingDirectory)
                installParsedSkill(
                    sourceRoot = skillRoot,
                    source = SkillInstallSource(
                        kind = SkillInstallKind.ZipUri,
                        label = label,
                        uri = zipUri.toString(),
                    ),
                )
            } finally {
                workingDirectory.deleteRecursively()
            }
        }
    }

    suspend fun installSkillFromRemote(
        rawUrl: String,
    ): Result<InstalledSkill> = withContext(Dispatchers.IO) {
        runCatching {
            val plan = resolveRemoteDownloadPlan(rawUrl)
            val workingDirectory = createTempDirectory()
            try {
                val zipBytes = downloadBytes(plan.downloadUrl)
                unzipIntoDirectory(zipBytes, workingDirectory)
                val skillRoot = locateSkillRoot(
                    root = workingDirectory,
                    requestedSubpath = plan.subpath,
                )
                installParsedSkill(
                    sourceRoot = skillRoot,
                    source = SkillInstallSource(
                        kind = plan.kind,
                        label = rawUrl,
                        uri = rawUrl,
                        ref = plan.ref,
                        subpath = plan.subpath,
                    ),
                )
            } finally {
                workingDirectory.deleteRecursively()
            }
        }
    }

    suspend fun uninstallSkill(skillId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val skill = extensionsRepository.extensionState.firstValue()
                .installedSkills
                .firstOrNull { it.id == skillId }
                ?: return@runCatching
            File(skill.skillRootPath).deleteRecursively()
            extensionsRepository.removeInstalledSkill(skillId)
        }
    }

    suspend fun buildActiveSkillContext(
        skill: InstalledSkill,
    ): Result<ActiveSkillContext> = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = parseSkillDocument(File(skill.skillMdPath))
            ActiveSkillContext(
                skillId = skill.id,
                name = parsed.name,
                description = parsed.description,
                compatibility = parsed.compatibility,
                allowedTools = parsed.allowedTools,
                skillRootPath = skill.skillRootPath,
                bodyMarkdown = parsed.bodyMarkdown,
                resourceEntries = skill.resourceEntries,
            )
        }
    }

    fun installedSkillsDirectory(): File = File(context.filesDir, SkillStorageDirectoryName).apply {
        mkdirs()
    }

    private suspend fun installParsedSkill(
        sourceRoot: File,
        source: SkillInstallSource,
    ): InstalledSkill {
        val skillFile = File(sourceRoot, SkillFileName)
        require(skillFile.isFile) { "The selected directory does not contain SKILL.md." }
        val parsed = parseSkillDocument(skillFile)
        val skillId = buildSkillId(parsed.name)
        val installRoot = File(installedSkillsDirectory(), skillId)
        val stagingRoot = File(context.filesDir, SkillTempDirectoryName)
            .apply { mkdirs() }
            .resolve("$skillId-${UUID.randomUUID()}")
            .apply { mkdirs() }
        sourceRoot.copyRecursively(stagingRoot, overwrite = true)
        val checksum = sha256OfDirectory(stagingRoot)
        installRoot.deleteRecursively()
        if (!stagingRoot.renameTo(installRoot)) {
            stagingRoot.copyRecursively(installRoot, overwrite = true)
            stagingRoot.deleteRecursively()
        }
        val installedSkill = InstalledSkill(
            id = skillId,
            name = parsed.name,
            description = parsed.description,
            actionLabel = generateQuickActionLabel(parsed.name, parsed.description),
            license = parsed.license,
            compatibility = parsed.compatibility,
            metadataJson = parsed.metadataJson,
            allowedTools = parsed.allowedTools,
            skillRootPath = installRoot.absolutePath,
            skillMdPath = File(installRoot, SkillFileName).absolutePath,
            source = source,
            checksumSha256 = checksum,
            diagnostics = parsed.diagnostics,
            resourceEntries = listSkillResources(installRoot),
        )
        extensionsRepository.upsertInstalledSkill(installedSkill)
        return installedSkill
    }

    private fun locateSkillRoot(
        root: File,
        requestedSubpath: String = "",
    ): File {
        val candidates = root.walkTopDown()
            .filter { it.isFile && it.name.equals(SkillFileName, ignoreCase = true) }
            .mapNotNull { it.parentFile }
            .toList()
        if (candidates.isEmpty()) {
            error("No SKILL.md file was found in the selected source.")
        }
        if (requestedSubpath.isBlank()) {
            return candidates.sortedBy { it.absolutePath.length }.first()
        }

        val normalizedSubpath = requestedSubpath.trim('/').replace('\\', '/')
        return candidates.firstOrNull { candidate ->
            candidate.relativeTo(root).invariantSeparatorsPath.endsWith(normalizedSubpath)
        } ?: candidates.sortedBy { it.absolutePath.length }.first()
    }

    private fun parseSkillDocument(skillFile: File): ParsedSkillDocument {
        val text = skillFile.readText()
        val (frontmatterText, bodyMarkdown) = splitFrontmatter(text)
        val diagnostics = mutableListOf<String>()
        val frontmatter = parseFrontmatter(frontmatterText, diagnostics)

        val name = readScalar(frontmatter, "name").ifBlank {
            extractFallbackScalar(frontmatterText, "name")
        }
        val description = readScalar(frontmatter, "description").ifBlank {
            extractFallbackScalar(frontmatterText, "description")
        }

        require(name.isNotBlank()) { "Skill frontmatter is missing 'name'." }
        require(description.isNotBlank()) { "Skill frontmatter is missing 'description'." }

        return ParsedSkillDocument(
            name = name,
            description = description,
            license = readScalar(frontmatter, "license"),
            compatibility = readFlexibleScalar(frontmatter, "compatibility"),
            metadataJson = readJsonValue(frontmatter["metadata"]),
            allowedTools = readStringList(frontmatter["allowed-tools"]),
            bodyMarkdown = bodyMarkdown.trim(),
            diagnostics = diagnostics,
        )
    }

    private fun splitFrontmatter(text: String): Pair<String, String> {
        if (!text.startsWith("---")) {
            return "" to text
        }
        val lines = text.lines()
        if (lines.isEmpty() || lines.first().trim() != "---") {
            return "" to text
        }
        val frontmatterLines = mutableListOf<String>()
        for (index in 1 until lines.size) {
            if (lines[index].trim() == "---") {
                val body = lines.drop(index + 1).joinToString("\n")
                return frontmatterLines.joinToString("\n") to body
            }
            frontmatterLines += lines[index]
        }
        return "" to text
    }

    private fun parseFrontmatter(
        rawFrontmatter: String,
        diagnostics: MutableList<String>,
    ): Map<String, Any?> {
        if (rawFrontmatter.isBlank()) return emptyMap()
        return runCatching {
            val loaderOptions = LoaderOptions().apply {
                isAllowDuplicateKeys = false
            }
            val yaml = Yaml(SafeConstructor(loaderOptions))
            @Suppress("UNCHECKED_CAST")
            yaml.load<Map<String, Any?>>(rawFrontmatter) ?: emptyMap()
        }.getOrElse { throwable ->
            diagnostics += throwable.message ?: "Couldn't parse SKILL.md frontmatter."
            emptyMap()
        }
    }

    private fun readScalar(
        frontmatter: Map<String, Any?>,
        key: String,
    ): String = frontmatter[key]?.toString()?.trim().orEmpty()

    private fun readFlexibleScalar(
        frontmatter: Map<String, Any?>,
        key: String,
    ): String {
        val value = frontmatter[key] ?: return ""
        return when (value) {
            is String -> value.trim()
            else -> readJsonValue(value)
        }
    }

    private fun readStringList(value: Any?): List<String> = when (value) {
        is String -> listOf(value.trim()).filter { it.isNotEmpty() }
        is Iterable<*> -> value.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
        else -> emptyList()
    }

    private fun extractFallbackScalar(
        frontmatterText: String,
        key: String,
    ): String {
        if (frontmatterText.isBlank()) return ""
        val prefix = "$key:"
        return frontmatterText.lineSequence()
            .firstOrNull { it.trimStart().startsWith(prefix) }
            ?.substringAfter(prefix)
            ?.trim()
            ?.trim('"')
            ?.trim('\'')
            .orEmpty()
    }

    private fun readJsonValue(value: Any?): String = when (value) {
        null -> "{}"
        is JSONObject -> value.toString()
        is JSONArray -> value.toString()
        is Map<*, *> -> JSONObject(value).toString()
        is Iterable<*> -> JSONArray(value.toList()).toString()
        else -> JSONObject.wrap(value)?.toString() ?: value.toString()
    }

    private fun listSkillResources(skillRoot: File): List<SkillResourceEntry> =
        skillRoot.walkTopDown()
            .filter { it.isFile }
            .map { file ->
                val relativePath = file.relativeTo(skillRoot).invariantSeparatorsPath
                SkillResourceEntry(
                    relativePath = relativePath,
                    kind = SkillResourceKind.fromRelativePath(relativePath),
                )
            }
            .sortedBy { it.relativePath }
            .toList()

    private fun createTempDirectory(): File =
        File(context.cacheDir, SkillTempDirectoryName)
            .apply { mkdirs() }
            .resolve(UUID.randomUUID().toString())
            .apply { mkdirs() }

    private fun copyDocumentTree(
        treeUri: Uri,
        destinationRoot: File,
    ): File {
        val rootDocument = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Couldn't open the selected folder.")
        val targetRoot = destinationRoot.resolve(rootDocument.name ?: "imported-skill")
        copyDocumentRecursively(rootDocument, targetRoot)
        return targetRoot
    }

    private fun copyDocumentRecursively(
        document: androidx.documentfile.provider.DocumentFile,
        destination: File,
    ) {
        if (document.isDirectory) {
            destination.mkdirs()
            document.listFiles().forEach { child ->
                copyDocumentRecursively(child, destination.resolve(child.name ?: child.uri.lastPathSegment ?: "file"))
            }
            return
        }

        destination.parentFile?.mkdirs()
        context.contentResolver.openInputStream(document.uri)?.use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        } ?: error("Couldn't read ${document.uri}.")
    }

    private fun unzipIntoDirectory(
        zipBytes: ByteArray,
        destinationRoot: File,
    ) {
        ZipInputStream(zipBytes.inputStream()).use { zipInput ->
            while (true) {
                val entry = zipInput.nextEntry ?: break
                val output = destinationRoot.resolve(entry.name)
                val canonicalRoot = destinationRoot.canonicalPath + File.separator
                val canonicalOutput = output.canonicalPath
                require(canonicalOutput.startsWith(canonicalRoot)) {
                    "Zip entry escaped the destination directory: ${entry.name}"
                }

                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    FileOutputStream(output).use { zipInput.copyTo(it) }
                }
                zipInput.closeEntry()
            }
        }
    }

    private fun resolveRemoteDownloadPlan(rawUrl: String): RemoteDownloadPlan {
        val url = rawUrl.trim().ifBlank { error("A remote skill URL is required.") }
        val httpUrl = url.toHttpUrlOrNull() ?: error("Skill URL is not a valid absolute URL.")
        if (httpUrl.host.equals("github.com", ignoreCase = true)) {
            val segments = httpUrl.pathSegments.filter { it.isNotBlank() }
            require(segments.size >= 2) { "GitHub URL must include owner and repository." }
            val owner = segments[0]
            val repository = segments[1].removeSuffix(".git")
            if (segments.size >= 4 && segments[2] == "tree") {
                val ref = segments[3]
                val subpath = segments.drop(4).joinToString("/")
                return RemoteDownloadPlan(
                    kind = SkillInstallKind.GitHub,
                    downloadUrl = "https://api.github.com/repos/$owner/$repository/zipball/$ref",
                    ref = ref,
                    subpath = subpath,
                )
            }
            return RemoteDownloadPlan(
                kind = SkillInstallKind.GitHub,
                downloadUrl = "https://api.github.com/repos/$owner/$repository/zipball",
            )
        }
        require(httpUrl.encodedPath.lowercase(Locale.US).endsWith(".zip")) {
            "Remote skill URL must be a GitHub repository/tree URL or a direct .zip file."
        }
        return RemoteDownloadPlan(
            kind = SkillInstallKind.RemoteZip,
            downloadUrl = httpUrl.toString(),
        )
    }

    private fun downloadBytes(url: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/zip, application/octet-stream")
            .addHeader("User-Agent", "Aether Android Agent")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Skill download failed with HTTP ${response.code}.")
            }
            return response.body?.bytes() ?: error("Skill download returned an empty body.")
        }
    }

    private fun buildSkillId(name: String): String =
        name.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "skill-${UUID.randomUUID()}" }

    private fun sha256OfDirectory(directory: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        directory.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.relativeTo(directory).invariantSeparatorsPath }
            .forEach { file ->
                digest.update(file.relativeTo(directory).invariantSeparatorsPath.toByteArray())
                digest.update(file.readBytes())
            }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstValue(): T = first()
}

private data class ParsedSkillDocument(
    val name: String,
    val description: String,
    val license: String,
    val compatibility: String,
    val metadataJson: String,
    val allowedTools: List<String>,
    val bodyMarkdown: String,
    val diagnostics: List<String>,
)

private data class RemoteDownloadPlan(
    val kind: SkillInstallKind,
    val downloadUrl: String,
    val ref: String = "",
    val subpath: String = "",
)
