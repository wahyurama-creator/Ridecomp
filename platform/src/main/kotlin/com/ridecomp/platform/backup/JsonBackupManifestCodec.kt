package com.ridecomp.platform.backup

import com.ridecomp.core.engine.backup.BackupManifest
import com.ridecomp.core.engine.backup.BackupManifestCodec
import org.json.JSONException
import org.json.JSONObject

/** `manifest.json` with org.json (decision D-26). Decoding never throws: bad input = not a backup. */
class JsonBackupManifestCodec : BackupManifestCodec {
    override fun encode(manifest: BackupManifest): ByteArray = JSONObject()
        .put(APP_VERSION, manifest.appVersion)
        .put(DB_SCHEMA_VERSION, manifest.dbSchemaVersion)
        .put(CREATED_AT, manifest.createdAtMs)
        .put(ACTIVITY_COUNT, manifest.activityCount)
        .put(ENCRYPTED, manifest.isEncrypted)
        .put(CHECKSUMS, JSONObject(manifest.checksums))
        .toString()
        .toByteArray(Charsets.UTF_8)

    override fun decode(bytes: ByteArray): BackupManifest? = try {
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        val checksums = json.getJSONObject(CHECKSUMS)
        BackupManifest(
            appVersion = json.getString(APP_VERSION),
            dbSchemaVersion = json.getInt(DB_SCHEMA_VERSION),
            createdAtMs = json.getLong(CREATED_AT),
            activityCount = json.getInt(ACTIVITY_COUNT),
            isEncrypted = json.getBoolean(ENCRYPTED),
            checksums = checksums.keys().asSequence().associateWith { checksums.getString(it) },
        )
    } catch (_: JSONException) {
        null
    }

    private companion object {
        const val APP_VERSION = "app_version"
        const val DB_SCHEMA_VERSION = "db_schema_version"
        const val CREATED_AT = "created_at"
        const val ACTIVITY_COUNT = "activity_count"
        const val ENCRYPTED = "encrypted"
        const val CHECKSUMS = "sha256"
    }
}
