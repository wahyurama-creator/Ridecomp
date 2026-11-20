package com.ridecomp.core.repository

import com.ridecomp.core.engine.map.MapImportError
import com.ridecomp.core.engine.map.OfflineMap
import com.ridecomp.core.util.Outcome
import kotlinx.coroutines.flow.Flow

/** Offline map region files in internal storage (T3.4, SEC-STO-01). */
public interface OfflineMapRepository {
    public fun observeAll(): Flow<List<OfflineMap>>

    /**
     * Validates the header, checks size and free space, then copies the picked document (SAF). At most
     * [maxBytes]: the room left under the total offline map limit (T11.14).
     */
    public suspend fun import(uri: String, name: String, maxBytes: Long): Outcome<OfflineMap, MapImportError>

    /**
     * Deletes leftover temporary files (T11.14): an import cut off by the app being killed, and partial pack
     * downloads whose file is not in [keepDownloads] (map names of the current index).
     */
    public suspend fun cleanTemporary(keepDownloads: Set<String>)

    public suspend fun delete(name: String)

    /** Free space in internal storage, for the storage card. */
    public suspend fun freeBytes(): Long

    /** Original document name, as a label only (SEC-IN-04). */
    public suspend fun displayName(uri: String): String?
}
