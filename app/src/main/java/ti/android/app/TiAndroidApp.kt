package ti.android.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class TiAndroidApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for structured logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.i("TiAndroidApp initialized [env=%s, v=%s]",
            BuildConfig.TI_ENV, BuildConfig.VERSION_NAME)
    }
}
