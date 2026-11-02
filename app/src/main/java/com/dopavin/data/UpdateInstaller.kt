package com.dopavin.data
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
object UpdateInstaller {
    enum class Reason { Network, Storage, Invalid, NotUpdate, Signature }
    sealed interface Download {
        data class Ok(val file: File) : Download
        data class Failed(val reason: Reason) : Download
    }
    enum class Install { Started, NeedsPermission, Failed }
    private fun dir(context: Context) = File(context.cacheDir, "updates")
    fun safeName(tag: String): String =
        "Dopavin-" + tag.filter { it.isLetterOrDigit() || it == '.' || it == '-' }.ifEmpty { "update" } + ".apk"
    fun fileFor(context: Context, tag: String) = File(dir(context), safeName(tag))
    suspend fun download(
        context: Context,
        url: String,
        tag: String,
        expectedSize: Long,
        onProgress: (Float) -> Unit,
    ): Download {
        if (!url.startsWith("https://")) return Download.Failed(Reason.Invalid)
        val folder = dir(context)
        if (!folder.exists() && !folder.mkdirs()) return Download.Failed(Reason.Storage)
        val target = fileFor(context, tag)
        val part = File(folder, target.name + ".part")
        part.delete()
        target.delete()
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Dopavin-update")
            if (conn.responseCode != 200) return Download.Failed(Reason.Network)
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            if (total > 0 && folder.usableSpace < total + SPACE_MARGIN) return Download.Failed(Reason.Storage)
            var done = 0L
            var lastStep = -1
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (total > 0) {
                            val step = (done * 100 / total).toInt().coerceIn(0, 100)
                            if (step != lastStep) {
                                lastStep = step
                                onProgress(step / 100f)
                            }
                        }
                    }
                }
            }
            if (total > 0 && done != total) return Download.Failed(Reason.Network)
            if (!part.renameTo(target)) return Download.Failed(Reason.Storage)
            return Download.Ok(target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            return Download.Failed(if (part.exists() && folder.usableSpace < SPACE_MARGIN) Reason.Storage else Reason.Network)
        } catch (e: Exception) {
            return Download.Failed(Reason.Network)
        } finally {
            conn?.disconnect()
            part.delete()
        }
    }
    fun verify(context: Context, file: File): Reason? {
        if (!file.isFile || file.length() == 0L) return Reason.Invalid
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = try {
            pm.getPackageArchiveInfo(file.path, flags)
        } catch (e: Exception) {
            null
        } ?: return Reason.Invalid
        if (archive.packageName != context.packageName) return Reason.NotUpdate
        val installed = try {
            pm.getPackageInfo(context.packageName, flags)
        } catch (e: Exception) {
            return Reason.Invalid
        }
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) return Reason.NotUpdate
        val a = signers(archive)
        val b = signers(installed)
        if (a != null && b != null && a != b) return Reason.Signature
        return null
    }
    @Suppress("DEPRECATION")
    private fun signers(info: android.content.pm.PackageInfo): Set<android.content.pm.Signature>? {
        val list = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return list?.takeIf { it.isNotEmpty() }?.toSet()
    }
    fun existing(context: Context, tag: String): File? =
        fileFor(context, tag).takeIf { verify(context, it) == null }
    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }
    fun install(context: Context, file: File): Install = try {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            Install.NeedsPermission
        } else {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            Install.Started
        }
    } catch (e: Exception) {
        Install.Failed
    }
    private const val SPACE_MARGIN = 20L * 1024 * 1024
}
