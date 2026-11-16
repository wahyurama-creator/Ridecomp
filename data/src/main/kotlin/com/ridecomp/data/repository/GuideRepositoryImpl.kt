package com.ridecomp.data.repository

import com.ridecomp.core.model.GuidePreferences
import com.ridecomp.core.model.GuideTip
import com.ridecomp.core.model.RiderLevel
import com.ridecomp.core.repository.GuideRepository
import com.ridecomp.data.db.dao.SettingsDao
import com.ridecomp.data.db.entity.SettingEntity
import com.ridecomp.data.mapper.SettingsKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Rider level and closed tips as two settings rows; unknown stored values are ignored. */
class GuideRepositoryImpl(private val dao: SettingsDao) : GuideRepository {
    private val mutex = Mutex()

    override fun observe(): Flow<GuidePreferences> = combine(
        dao.observe(SettingsKeys.GUIDE_LEVEL),
        dao.observe(SettingsKeys.GUIDE_DISMISSED_TIPS),
    ) { level, dismissed -> GuidePreferences(RiderLevel.fromStorage(level), parse(dismissed)) }.distinctUntilChanged()

    override suspend fun setLevel(level: RiderLevel) {
        dao.upsert(SettingEntity(SettingsKeys.GUIDE_LEVEL, level.storageValue))
    }

    /** Read-modify-write under a lock so two tips closed at once are both kept (single instance). */
    override suspend fun dismiss(tip: GuideTip) {
        mutex.withLock {
            val dismissed = parse(dao.get(SettingsKeys.GUIDE_DISMISSED_TIPS)) + tip
            val stored = dismissed.joinToString(SEPARATOR) { it.storageKey }
            dao.upsert(SettingEntity(SettingsKeys.GUIDE_DISMISSED_TIPS, stored))
        }
    }

    override suspend fun resetTips() {
        dao.delete(SettingsKeys.GUIDE_DISMISSED_TIPS)
    }

    private fun parse(stored: String?): Set<GuideTip> = stored.orEmpty().split(SEPARATOR)
        .mapNotNull { key -> GuideTip.entries.firstOrNull { it.storageKey == key } }
        .toSet()

    private companion object {
        const val SEPARATOR = ","
    }
}
