package dev.axp.layoutlib.worker.classloader

import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CACHE_RELATIVE_DIR
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_CLASSES_JAR_ENTRY
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.AAR_EXTENSION
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.CLASS_FILE_SUFFIX
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.EXTRACTED_JAR_SUFFIX
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.REWRITE_VERSION
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.SHA1_DIGEST_NAME
import dev.axp.layoutlib.worker.classloader.ClassLoaderConstants.TEMP_JAR_SUFFIX
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.isRegularFile

/**
 * AAR(ZIP) 안의 classes.jar 를 stable cache 에 추출. mtime 기반 idempotent + atomic write.
 *
 * Cache 위치: `<cacheRoot>/aar-classes/<REWRITE_VERSION>/<sha1(absPath)>/<artifactName>.jar`.
 * 추출 과정에서 각 .class entry 를 AndroidClassRewriter 로 변환하여 host-JVM 에서 실재하지 않는
 * `android/os/Build` 계열 reference 를 layoutlib 의 `_Original_Build` 로 rewrite. NAME_MAP 변경
 * 시 stale cache 회피용으로 path 에 REWRITE_VERSION layer 포함.
 *
 * AAR 안에 classes.jar 없는 경우 (resource-only) → null.
 * 손상된 ZIP → ZipException pass-through.
 */
object AarExtractor {

    fun extract(aarPath: Path, cacheRoot: Path): Path? {
        require(aarPath.isRegularFile()) { "AAR 누락: $aarPath" }
        val key = sha1(aarPath.toAbsolutePath().toString())
        val artifactName = aarPath.fileName.toString().removeSuffix(AAR_EXTENSION)
        val outDir = cacheRoot.resolve(AAR_CACHE_RELATIVE_DIR)
            .resolve(REWRITE_VERSION)
            .resolve(key)
        Files.createDirectories(outDir)
        val outJar = outDir.resolve(artifactName + EXTRACTED_JAR_SUFFIX)

        val aarMtime = Files.getLastModifiedTime(aarPath).toMillis()
        if (outJar.isRegularFile() &&
            Files.getLastModifiedTime(outJar).toMillis() >= aarMtime)
        {
            return outJar
        }

        val tmpJar = outDir.resolve(artifactName + TEMP_JAR_SUFFIX)
        ZipFile(aarPath.toFile()).use { aarZip ->
            val entry = aarZip.getEntry(AAR_CLASSES_JAR_ENTRY) ?: return null
            aarZip.getInputStream(entry).use { input ->
                rewriteClassesJar(input, tmpJar)
            }
        }
        Files.move(tmpJar, outJar, ATOMIC_MOVE, REPLACE_EXISTING)
        return outJar
    }

    /**
     * classes.jar stream 을 read 하여 각 .class entry 는 AndroidClassRewriter 로 변환,
     * 그 외 entry 는 byte-copy. 결과를 outPath 의 새 ZIP 으로 emit.
     */
    private fun rewriteClassesJar(input: InputStream, outPath: Path) {
        ZipInputStream(input).use { zin ->
            ZipOutputStream(Files.newOutputStream(outPath)).use { zout ->
                var entry = zin.nextEntry
                while (entry != null)
                {
                    val bytes = zin.readBytes()
                    val rewritten = if (entry.name.endsWith(CLASS_FILE_SUFFIX))
                    {
                        AndroidClassRewriter.rewrite(bytes)
                    }
                    else
                    {
                        bytes
                    }
                    zout.putNextEntry(ZipEntry(entry.name))
                    zout.write(rewritten)
                    zout.closeEntry()
                    entry = zin.nextEntry
                }
            }
        }
    }

    private fun sha1(s: String): String {
        val md = MessageDigest.getInstance(SHA1_DIGEST_NAME)
        return md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
