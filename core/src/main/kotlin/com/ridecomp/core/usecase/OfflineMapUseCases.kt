package com.ridecomp.core.usecase

import com.ridecomp.core.engine.map.MapImportError
import com.ridecomp.core.engine.map.OfflineMap
import com.ridecomp.core.engine.map.OfflineMapConfig
import com.ridecomp.core.repository.OfflineMapRepository
import com.ridecomp.core.util.Outcome
import kotlinx.coroutines.flow.Flow

public class ObserveOfflineMapsUseCase(private val repository: OfflineMapRepository) {
    public operator fun invoke(): Flow<List<OfflineMap>> = repository.observeAll()
}

/**
 * Imports a PMTiles region picked by the user (T3.4). The stored name comes from the document name,
 * reduced to letters, digits and `-_` so it is safe as a file name (SEC-IN-04).
 */
public class ImportOfflineMapUseCase(private val repository: OfflineMapRepository) {
    public suspend operator fun invoke(uri: String, fallbackName: String): Outcome<OfflineMap, MapImportError> {
        val label = repository.displayName(uri)?.removeSuffix(OfflineMapConfig.FILE_EXTENSION)
        return repository.import(uri, safeName(label ?: fallbackName).ifEmpty { safeName(fallbackName) })
    }

    private fun safeName(text: String): String = text
        .map { if (it.isLetterOrDigit() || it in SAFE_PUNCTUATION) it else REPLACEMENT }
        .joinToString("")
        .trim(REPLACEMENT)
        .take(OfflineMapConfig.MAX_NAME_LENGTH)

    private companion object {
        val SAFE_PUNCTUATION = setOf('-', '_')
        const val REPLACEMENT = '_'
    }
}

public class DeleteOfflineMapUseCase(private val repository: OfflineMapRepository) {
    public suspend operator fun invoke(name: String) {
        repository.delete(name)
    }
}

public class GetFreeStorageUseCase(private val repository: OfflineMapRepository) {
    public suspend operator fun invoke(): Long = repository.freeBytes()
}
