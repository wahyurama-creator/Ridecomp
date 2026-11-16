package com.ridecomp.data.mapper

import com.ridecomp.core.model.StravaUpload
import com.ridecomp.core.model.StravaUploadError
import com.ridecomp.core.model.StravaUploadState
import com.ridecomp.data.db.entity.StravaUploadEntity

/** Stored names of upload states and errors: lowercase enum names, stable across releases. */
object StravaUploadMapper {
    fun toEntity(upload: StravaUpload): StravaUploadEntity = StravaUploadEntity(
        activityId = upload.activityId,
        state = upload.state.name.lowercase(),
        uploadId = upload.uploadId,
        stravaActivityId = upload.stravaActivityId,
        error = upload.error?.name?.lowercase(),
        updatedAt = upload.updatedAtMs,
    )

    /** An unknown state (from a newer version) counts as failed, so the rider can send it again. */
    fun toDomain(entity: StravaUploadEntity): StravaUpload = StravaUpload(
        activityId = entity.activityId,
        state = StravaUploadState.entries.firstOrNull { it.name.lowercase() == entity.state }
            ?: StravaUploadState.FAILED,
        uploadId = entity.uploadId,
        stravaActivityId = entity.stravaActivityId,
        error = StravaUploadError.entries.firstOrNull { it.name.lowercase() == entity.error },
        updatedAtMs = entity.updatedAt,
    )

    fun storedName(state: StravaUploadState): String = state.name.lowercase()
}
