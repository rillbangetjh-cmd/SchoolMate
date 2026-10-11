package id.schoolmate.app

import android.app.Application
import id.schoolmate.app.di.AppContainer
import id.schoolmate.app.di.DefaultAppContainer

/**
 * Application entry point. Owns the [AppContainer] (manual dependency injection),
 * so every dependency is created lazily once per process.
 */
class SchoolMateApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        // Channels must exist before the first notification; creating them again is a no-op.
        container.reminderNotifier.createChannels()
    }
}
