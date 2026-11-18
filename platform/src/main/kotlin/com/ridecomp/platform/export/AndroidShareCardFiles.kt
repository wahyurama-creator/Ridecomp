package com.ridecomp.platform.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.ridecomp.core.engine.share.ShareCardConfig
import com.ridecomp.core.engine.share.ShareTarget
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.usecase.ShareCardFiles
import com.ridecomp.core.util.runCatchingCancellable
import java.io.File
import kotlinx.coroutines.withContext

/**
 * Share card PNGs in `cacheDir/share` (12-FEATURE-SPECS §11): app-generated names only, shared with a
 * temporary read grant through the FileProvider (SEC-PLT-04), deleted at the next app start.
 */
class AndroidShareCardFiles(private val context: Context, private val dispatchers: DispatcherProvider) :
    ShareCardFiles {
    private val authority: String = context.packageName + ExportPaths.AUTHORITY_SUFFIX

    // Resolved inside the IO calls: `cacheDir` touches the disk.
    private val directory by lazy { File(context.cacheDir, ExportPaths.SHARE_DIRECTORY) }

    override suspend fun save(fileName: String, png: ByteArray): String = withContext(dispatchers.io) {
        directory.mkdirs()
        File(directory, fileName).apply { writeBytes(png) }.absolutePath
    }

    override suspend fun share(path: String, fileName: String, target: ShareTarget) {
        val uri = withContext(dispatchers.io) { FileProvider.getUriForFile(context, authority, File(path)) }
        val send = Intent(Intent.ACTION_SEND)
            .setType(ShareCardConfig.MIME_TYPE)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(fileName, uri)
        // A direct target opens that app's own "share to" screen (story, post or chat); the rest get the sheet.
        val intent = target.packageName()?.let { send.setPackage(it) } ?: Intent.createChooser(send, fileName)
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    /** Needs the `<queries>` entries of these packages in the manifest (package visibility, Android 11+). */
    override fun installedTargets(): Set<ShareTarget> = ShareTarget.entries.filter { target ->
        target.packageName()?.let(::isInstalled) == true
    }.toSet()

    private fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    override suspend fun saveTo(destinationUri: String, png: ByteArray): Boolean = withContext(dispatchers.io) {
        runCatchingCancellable {
            val output = requireNotNull(context.contentResolver.openOutputStream(destinationUri.toUri())) {
                "Cannot open destination"
            }
            output.use { it.write(png) }
        }.isSuccess
    }

    override suspend fun clear() {
        withContext(dispatchers.io) {
            directory.listFiles()?.filter { it.name.startsWith(ShareCardConfig.FILE_PREFIX) }?.forEach { it.delete() }
        }
    }
}

/** The app a direct target opens; null for the share sheet. */
private fun ShareTarget.packageName(): String? = when (this) {
    ShareTarget.INSTAGRAM -> ExportPaths.INSTAGRAM_PACKAGE
    ShareTarget.WHATSAPP -> ExportPaths.WHATSAPP_PACKAGE
    ShareTarget.OTHER -> null
}
