package id.schoolmate.app.di

import android.content.Context
import id.schoolmate.app.data.local.SchoolMateDatabase
import id.schoolmate.app.data.preferences.DataStoreUserPreferencesRepository
import id.schoolmate.app.data.preferences.UserPreferencesRepository
import id.schoolmate.app.data.preferences.userPreferencesDataStore
import id.schoolmate.app.data.repository.OfflineSubjectRepository
import id.schoolmate.app.data.repository.SubjectRepository

/** Manual dependency container. Keeps the dependency graph explicit and free of codegen. */
interface AppContainer {
    val userPreferencesRepository: UserPreferencesRepository
    val subjectRepository: SubjectRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext

    private val database: SchoolMateDatabase by lazy { SchoolMateDatabase.getInstance(appContext) }

    override val userPreferencesRepository: UserPreferencesRepository by lazy {
        DataStoreUserPreferencesRepository(appContext.userPreferencesDataStore)
    }

    override val subjectRepository: SubjectRepository by lazy {
        OfflineSubjectRepository(database.subjectDao())
    }
}
