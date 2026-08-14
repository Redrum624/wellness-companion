package com.wellnesscompanion.app.data.repository

import com.google.gson.Gson
import com.wellnesscompanion.app.data.local.dao.ChoreTemplateDao
import com.wellnesscompanion.app.data.local.dao.EntryDao
import com.wellnesscompanion.app.data.local.dao.HobbyDao
import com.wellnesscompanion.app.data.local.dao.PersonDao
import com.wellnesscompanion.app.data.local.dao.SettingsDao
import com.wellnesscompanion.app.data.local.entity.ChoreTemplateEntity
import com.wellnesscompanion.app.data.local.entity.EntryEntity
import com.wellnesscompanion.app.data.local.entity.HobbyEntity
import com.wellnesscompanion.app.data.local.entity.PersonEntity
import com.wellnesscompanion.app.data.local.entity.SettingEntity
import com.wellnesscompanion.app.util.nowMillis
import com.wellnesscompanion.app.util.todayDateString
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepository @Inject constructor(
    private val entryDao: EntryDao,
    private val settingsDao: SettingsDao,
    private val choreTemplateDao: ChoreTemplateDao,
    private val hobbyDao: HobbyDao,
    private val personDao: PersonDao,
    private val gson: Gson
) {
    fun getTodayEntries(category: String): Flow<List<EntryEntity>> =
        entryDao.getEntriesByDateAndCategory(todayDateString(), category)

    fun getAllTodayEntries(): Flow<List<EntryEntity>> =
        entryDao.getEntriesByDate(todayDateString())

    fun getEntriesByDateAndCategory(date: String, category: String): Flow<List<EntryEntity>> =
        entryDao.getEntriesByDateAndCategory(date, category)

    fun getLatestEntry(category: String): Flow<EntryEntity?> =
        entryDao.getLatestByCategory(category)

    suspend fun addEntry(category: String, data: Any): EntryEntity =
        addEntryForDate(category, data, todayDateString())

    suspend fun addEntryForDate(category: String, data: Any, date: String): EntryEntity {
        val now = nowMillis()
        val entry = EntryEntity(
            id = UUID.randomUUID().toString(),
            category = category,
            timestamp = now,
            date = date,
            data = gson.toJson(data),
            version = 1,
            modifiedAt = now,
            synced = 0
        )
        entryDao.insert(entry)
        return entry
    }

    suspend fun updateEntry(entry: EntryEntity) {
        entryDao.update(entry.copy(
            version = entry.version + 1,
            modifiedAt = nowMillis()
        ))
    }

    suspend fun deleteEntry(entry: EntryEntity) {
        entryDao.delete(entry)
    }

    suspend fun deleteEntryById(id: String) {
        entryDao.deleteById(id)
    }

    suspend fun getSetting(key: String): String? = settingsDao.getSetting(key)

    fun getSettingFlow(key: String): Flow<String?> = settingsDao.getSettingFlow(key)

    suspend fun setSetting(key: String, value: String) {
        settingsDao.setSetting(SettingEntity(key, value))
    }

    // Date range queries for weekly trends
    fun getEntriesByDateRange(startDate: String, endDate: String, category: String): Flow<List<EntryEntity>> =
        entryDao.getEntriesByDateRange(startDate, endDate, category)

    fun getLoggedDates(category: String): Flow<List<String>> =
        entryDao.getLoggedDates(category)

    // Chore templates
    fun getChoreTemplates(): Flow<List<ChoreTemplateEntity>> = choreTemplateDao.getAll()
    suspend fun addChoreTemplate(template: ChoreTemplateEntity) = choreTemplateDao.insert(template)
    suspend fun deleteChoreTemplate(template: ChoreTemplateEntity) = choreTemplateDao.delete(template)

    // Hobbies
    fun getHobbies(): Flow<List<HobbyEntity>> = hobbyDao.getAll()
    suspend fun addHobby(hobby: HobbyEntity) = hobbyDao.insert(hobby)
    suspend fun deleteHobby(hobby: HobbyEntity) = hobbyDao.delete(hobby)

    // People
    fun getPeople(): Flow<List<PersonEntity>> = personDao.getAll()
    suspend fun addPerson(person: PersonEntity) = personDao.insert(person)
    suspend fun deletePerson(person: PersonEntity) = personDao.delete(person)
}
