package com.ridecomp.data.repository

import com.ridecomp.core.model.EmergencyContact
import com.ridecomp.core.model.SafetySettings
import com.ridecomp.core.repository.SafetyRepository
import com.ridecomp.data.db.dao.EmergencyContactDao
import com.ridecomp.data.db.dao.SettingsDao
import com.ridecomp.data.db.entity.EmergencyContactEntity
import com.ridecomp.data.db.entity.SettingEntity
import com.ridecomp.data.mapper.SettingsKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Room implementation of [SafetyRepository]; settings in the key-value table, contacts in their own table. */
class SafetyRepositoryImpl(private val contactDao: EmergencyContactDao, private val settingsDao: SettingsDao) :
    SafetyRepository {
    override fun observeSettings(): Flow<SafetySettings> = combine(
        settingsDao.observe(SettingsKeys.SAFETY_ENABLED),
        settingsDao.observe(SettingsKeys.SAFETY_RIDER_NAME),
    ) { enabled, name -> settingsOf(enabled, name) }.distinctUntilChanged()

    override suspend fun getSettings(): SafetySettings =
        settingsOf(settingsDao.get(SettingsKeys.SAFETY_ENABLED), settingsDao.get(SettingsKeys.SAFETY_RIDER_NAME))

    override suspend fun saveSettings(settings: SafetySettings) {
        settingsDao.upsertAll(
            listOf(
                SettingEntity(SettingsKeys.SAFETY_ENABLED, settings.isEnabled.toString()),
                SettingEntity(SettingsKeys.SAFETY_RIDER_NAME, settings.riderName),
            ),
        )
    }

    override fun observeContacts(): Flow<List<EmergencyContact>> =
        contactDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getContacts(): List<EmergencyContact> = contactDao.getAll().map { it.toDomain() }

    override suspend fun addContact(contact: EmergencyContact) {
        contactDao.insert(
            EmergencyContactEntity(
                displayName = contact.displayName,
                phone = contact.phone,
                sortOrder = contact.sortOrder,
            ),
        )
    }

    override suspend fun removeContact(id: Long) {
        contactDao.delete(id)
    }

    override suspend fun getLastTestSmsAtMs(): Long? =
        settingsDao.get(SettingsKeys.SAFETY_LAST_TEST_SMS_AT)?.toLongOrNull()

    override suspend fun setLastTestSmsAtMs(atMs: Long) {
        settingsDao.upsert(SettingEntity(SettingsKeys.SAFETY_LAST_TEST_SMS_AT, atMs.toString()))
    }

    private fun settingsOf(enabled: String?, name: String?) =
        SafetySettings(isEnabled = enabled?.toBooleanStrictOrNull() == true, riderName = name.orEmpty())

    private fun EmergencyContactEntity.toDomain() = EmergencyContact(id, displayName, phone, sortOrder)
}
