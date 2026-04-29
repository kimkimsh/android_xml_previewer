package dev.axp.layoutlib.worker.classloader

import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CACHE_RELATIVE_DIR
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CLASSES_JAR_ENTRY
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.EXTRACTED_JAR_SUFFIX
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.SHA1_DIGEST_NAME
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.TEMP_JAR_SUFFIX
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream

/**
 * AAR(ZIP) 안의 classes.jar 를 stable cache 에 추출. mtime 기반 idempotent + atomic write.
 *
 * Cache 위치: `<cacheRoot>/aar-classes/<sha1(absPath)>/<artifactName>.jar`.
 * AAR 안에 classes.jar 없는 경우 (resource-only) → null.
 * 손상된 ZIP → ZipException pass-through.
 */
object AarExtractor {

    fun extract(aarPath: Path, cacheRoot: Path): Path? {
        require(aarPath.isRegularFile()) { "AAR 누락: $aarPath" }
        val key = sha1(aarPath.toAbsolutePath().toString())
        val artifactName = aarPath.fileName.toString().removeSuffix(AAR_EXTENSION)
        val outDir = cacheRoot.resolve(AAR_CACHE_RELATIVE_DIR).resolve(key)
        Files.createDirectories(outDir)
        val outJar = outDir.resolve(artifactName + EXTRACTED_JAR_SUFFIX)

        val aarMtime = Files.getLastModifiedTime(aarPath).toMillis()
        if (outJar.isRegularFile() &&
            Files.getLastModifiedTime(outJar).toMillis() >= aarMtime)
        {
            return outJar
        }

        val tmpJar = outDir.resolve(artifactName + TEMP_JAR_SUFFIX)
        ZipFile(aarPath.toFile()).use { zip ->
            val entry = zip.getEntry(AAR_CLASSES_JAR_ENTRY) ?: return null
            zip.getInputStream(entry).use { input ->
                tmpJar.outputStream().use { output -> input.copyTo(output) }
            }
        }
        Files.move(tmpJar, outJar, ATOMIC_MOVE, REPLACE_EXISTING)
        return outJar
    }

    private fun sha1(s: String): String {
        val md = MessageDigest.getInstance(SHA1_DIGEST_NAME)
        return md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
