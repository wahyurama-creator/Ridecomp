package com.ridecomp.core.usecase

import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.util.Outcome
import com.ridecomp.core.util.asFailure
import com.ridecomp.core.util.asSuccess
import com.ridecomp.core.util.flatMap
import com.ridecomp.core.util.onSuccess
import java.time.ZoneId

/** Builds the requested file of a ride. */
public class ExportActivityUseCase(private val exportFit: ExportFitUseCase, private val exportGpx: ExportGpxUseCase) {
    public suspend operator fun invoke(activityId: Long, format: ExportFormat): Outcome<ExportedFile, ExportError> =
        when (format) {
            ExportFormat.FIT -> exportFit(activityId)
            ExportFormat.GPX -> exportGpx(activityId)
        }
}

/** Exports and opens the share sheet (Strava app, Drive, WhatsApp…) — 04-INTEGRATIONS §E.1. */
public class ShareActivityFileUseCase(
    private val export: ExportActivityUseCase,
    private val handler: ExportedFileHandler,
) {
    public suspend operator fun invoke(activityId: Long, format: ExportFormat): Outcome<ExportedFile, ExportError> =
        export(activityId, format).onSuccess { handler.share(it) }
}

/** Exports and copies the file to a place the user picked ("Save to Downloads", SAF). */
public class SaveActivityFileUseCase(
    private val export: ExportActivityUseCase,
    private val handler: ExportedFileHandler,
) {
    public suspend operator fun invoke(
        activityId: Long,
        format: ExportFormat,
        destinationUri: String,
    ): Outcome<ExportedFile, ExportError> = export(activityId, format).flatMap { file ->
        if (handler.copyTo(file, destinationUri)) file.asSuccess() else ExportError.WRITE_FAILED.asFailure()
    }
}

/** Suggested document name for the SAF "create document" dialog. */
public class SuggestExportFileNameUseCase(private val repository: ActivityRepository, private val zone: ZoneId) {
    public suspend operator fun invoke(activityId: Long, format: ExportFormat): String? =
        repository.get(activityId)?.let { ExportFileNames.of(it, format, zone) }
}
