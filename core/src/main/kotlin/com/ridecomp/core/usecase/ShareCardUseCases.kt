package com.ridecomp.core.usecase

import com.ridecomp.core.engine.share.LinePoint
import com.ridecomp.core.engine.share.ShareCardConfig
import com.ridecomp.core.model.Activity
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.BestEffortRepository
import com.ridecomp.core.source.Clock
import com.ridecomp.core.source.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * Share card images (12-FEATURE-SPECS §11): written to the app cache, shared through the FileProvider
 * (SEC-PLT-04) and deleted at the next app start. Implemented in :platform.
 */
public interface ShareCardFiles {
    /** Writes the PNG and returns its absolute path. */
    public suspend fun save(fileName: String, png: ByteArray): String

    /** Opens the Android share sheet (WhatsApp, Instagram…) for the image. */
    public suspend fun share(path: String, fileName: String)

    /** Deletes every card written before. */
    public suspend fun clear()
}

/** What a card is made of: the ride, its new records and its line (not yet trimmed). */
public data class ShareCardData(val activity: Activity, val newRecords: Int, val line: List<LinePoint>)

public class LoadShareCardUseCase(
    private val activities: ActivityRepository,
    private val efforts: BestEffortRepository,
    private val readTrack: ReadRideTrackUseCase,
    private val dispatchers: DispatcherProvider,
) {
    public suspend operator fun invoke(activityId: Long): ShareCardData? {
        val activity = activities.get(activityId) ?: return null
        val track = readTrack(activityId)
        val line = withContext(dispatchers.default) {
            (0 until track.size).filter(track::hasPosition)
                .map { LinePoint(track.latDeg(it), track.lonDeg(it), track.distanceM(it)) }
        }
        return ShareCardData(activity, efforts.countRecords(activityId), line)
    }
}

/** Saves the rendered card and opens the share sheet. */
public class ShareCardImageUseCase(private val files: ShareCardFiles, private val clock: Clock) {
    public suspend operator fun invoke(png: ByteArray) {
        val fileName = ShareCardConfig.FILE_PREFIX + clock.nowMs() + ShareCardConfig.FILE_EXTENSION
        files.share(files.save(fileName, png), fileName)
    }
}

/** Removes the cards of earlier sessions (§11: "dihapus saat app dibuka berikutnya"). */
public class ClearShareCardsUseCase(private val files: ShareCardFiles) {
    public suspend operator fun invoke() {
        files.clear()
    }
}
