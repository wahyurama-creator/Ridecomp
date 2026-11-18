package com.ridecomp.core.usecase

import com.ridecomp.core.engine.share.LinePoint
import com.ridecomp.core.engine.share.ShareCardConfig
import com.ridecomp.core.engine.share.ShareCardDetails
import com.ridecomp.core.engine.share.ShareDetails
import com.ridecomp.core.engine.share.ShareTarget
import com.ridecomp.core.engine.training.ZoneTime
import com.ridecomp.core.model.Activity
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.BestEffortRepository
import com.ridecomp.core.repository.SegmentRepository
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

    /** Sends the image to [target], or opens the Android share sheet for [ShareTarget.OTHER]. */
    public suspend fun share(path: String, fileName: String, target: ShareTarget)

    /** The direct targets installed on the phone (T10.6); [ShareTarget.OTHER] is always available. */
    public fun installedTargets(): Set<ShareTarget>

    /** Writes the PNG to a document the rider picked ("Simpan gambar", SAF); false when it failed. */
    public suspend fun saveTo(destinationUri: String, png: ByteArray): Boolean

    /** Deletes every card written before. */
    public suspend fun clear()
}

/** What a card is made of: the ride, its line (not yet trimmed) and its training details. */
public data class ShareCardData(
    val activity: Activity,
    val line: List<LinePoint>,
    val details: ShareDetails = ShareDetails(),
) {
    val newRecords: Int get() = details.records
}

public class LoadShareCardUseCase(
    private val activities: ActivityRepository,
    private val efforts: BestEffortRepository,
    private val segments: SegmentRepository,
    private val loadInputs: GetLoadInputsUseCase,
    private val readTrack: ReadRideTrackUseCase,
    private val dispatchers: DispatcherProvider,
) {
    public suspend operator fun invoke(activityId: Long): ShareCardData? {
        val activity = activities.get(activityId) ?: return null
        val track = readTrack(activityId)
        val hrMax = loadInputs().hrMaxBpm
        val (line, details) = withContext(dispatchers.default) {
            val line = (0 until track.size).filter(track::hasPosition)
                .map { LinePoint(track.latDeg(it), track.lonDeg(it), track.distanceM(it)) }
            line to ShareDetails(
                elevationM = ShareCardDetails.elevation(track),
                zonesS = ZoneTime.heartRate(track, hrMax),
                splitsS = ShareCardDetails.splits(track),
            )
        }
        val withRecords = details.copy(
            records = efforts.countRecords(activityId),
            segmentRecords = segments.countRecords(activityId),
        )
        return ShareCardData(activity, line, withRecords)
    }
}

/** Saves the rendered card and sends it to the chosen app (or the share sheet). */
public class ShareCardImageUseCase(private val files: ShareCardFiles, private val clock: Clock) {
    public suspend operator fun invoke(png: ByteArray, target: ShareTarget = ShareTarget.OTHER) {
        val fileName = ShareCardConfig.FILE_PREFIX + clock.nowMs() + ShareCardConfig.FILE_EXTENSION
        files.share(files.save(fileName, png), fileName, target)
    }
}

/** The share buttons to show: direct apps that are installed, then "more" (T10.6). */
public class GetShareTargetsUseCase(private val files: ShareCardFiles) {
    public operator fun invoke(): List<ShareTarget> = files.installedTargets().let { installed ->
        ShareTarget.entries.filter {
            it == ShareTarget.OTHER ||
                it in installed
        }
    }
}

/** Saves the rendered card where the rider chose (download). */
public class SaveShareCardUseCase(private val files: ShareCardFiles) {
    public suspend operator fun invoke(destinationUri: String, png: ByteArray): Boolean =
        files.saveTo(destinationUri, png)
}

/** Removes the cards of earlier sessions (§11: "dihapus saat app dibuka berikutnya"). */
public class ClearShareCardsUseCase(private val files: ShareCardFiles) {
    public suspend operator fun invoke() {
        files.clear()
    }
}
