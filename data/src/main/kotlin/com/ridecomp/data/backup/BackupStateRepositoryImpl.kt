package com.ridecomp.data.backup

import com.ridecomp.core.usecase.BackupStateRepository
import com.ridecomp.data.db.dao.SettingsDao
import com.ridecomp.data.db.entity.SettingEntity
import com.ridecomp.data.mapper.SettingsKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Last backup facts in the key-value `settings` table (12-FEATURE-SPECS §12.3–12.4). */
class BackupStateRepositoryImpl(private val dao: SettingsDao) : BackupStateRepository {
    override fun observeLastBackupAtMs(): Flow<Long?> = longValue(SettingsKeys.BACKUP_LAST_AT)

    override fun observeActivityCountAtBackup(): Flow<Int> =
        dao.observe(SettingsKeys.BACKUP_ACTIVITY_COUNT).map { it?.toIntOrNull() ?: 0 }.distinctUntilChanged()

    override fun observeReminderSnoozedUntilMs(): Flow<Long?> = longValue(SettingsKeys.BACKUP_SNOOZED_UNTIL)

    override suspend fun recordBackup(atMs: Long, activityCount: Int) {
        dao.upsertAll(
            listOf(
                SettingEntity(SettingsKeys.BACKUP_LAST_AT, atMs.toString()),
                SettingEntity(SettingsKeys.BACKUP_ACTIVITY_COUNT, activityCount.toString()),
            ),
        )
    }

    override suspend fun snoozeReminder(untilMs: Long) {
        dao.upsert(SettingEntity(SettingsKeys.BACKUP_SNOOZED_UNTIL, untilMs.toString()))
    }

    private fun longValue(key: String): Flow<Long?> = dao.observe(key).map { it?.toLongOrNull() }.distinctUntilChanged()
}
