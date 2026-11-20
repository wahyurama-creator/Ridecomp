package com.ridecomp.platform.strava

import android.content.Context
import com.ridecomp.core.model.StravaAccount
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.Logger
import com.ridecomp.core.source.StravaAccountStore
import com.ridecomp.core.util.runCatchingCancellable
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The Strava app and tokens in one encrypted file in `noBackupFilesDir` (D-79): excluded from cloud backup
 * and device transfer by Android, readable only with this phone's Keystore key (SEC-STO-02/03). An
 * unreadable file (new phone, cleared key) counts as "not set up". Single instance; the value is cached.
 */
class AndroidStravaAccountStore(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) : StravaAccountStore {
    // Resolved on the IO thread: `noBackupFilesDir` creates the folder, a disk access (STB-01). The store is
    // created by Koin on the main thread at app start.
    private val file by lazy { File(context.noBackupFilesDir, FILE_NAME) }
    private val cipher = KeystoreCipher(KEY_ALIAS)
    private val mutex = Mutex()
    private val cache = MutableStateFlow<StravaAccount?>(null)

    override fun observe(): Flow<StravaAccount> = cache.onStart { get() }.filterNotNull()

    override suspend fun get(): StravaAccount = mutex.withLock { loaded() }

    override suspend fun update(transform: (StravaAccount) -> StravaAccount): StravaAccount = mutex.withLock {
        val current = loaded()
        val next = transform(current)
        if (next != current) {
            withContext(dispatchers.io) { write(next) }
            cache.value = next
        }
        next
    }

    private suspend fun loaded(): StravaAccount =
        cache.value ?: withContext(dispatchers.io) { read() }.also { cache.value = it }

    private fun read(): StravaAccount {
        if (!file.exists()) return StravaAccount()
        return runCatchingCancellable {
            StravaJson.decodeAccount(cipher.decrypt(file.readBytes()).toString(Charsets.UTF_8))
        }.getOrNull() ?: StravaAccount().also {
            // Never log the content: only that it was dropped.
            logger.warn(TAG) { "Strava account file unreadable; removed" }
            file.delete()
        }
    }

    private fun write(account: StravaAccount) {
        if (account == StravaAccount()) {
            file.delete()
            return
        }
        val data = cipher.encrypt(StravaJson.encodeAccount(account).toByteArray(Charsets.UTF_8))
        // Write then rename: a crash mid-write never leaves half a file.
        val temp = File(file.parentFile, "$FILE_NAME$TEMP_SUFFIX")
        temp.writeBytes(data)
        check(temp.renameTo(file)) { "Could not store the Strava account" }
    }

    private companion object {
        const val FILE_NAME = "strava_account.bin"
        const val TEMP_SUFFIX = ".tmp"
        const val KEY_ALIAS = "ridecomp.strava"
        const val TAG = "Strava"
    }
}
