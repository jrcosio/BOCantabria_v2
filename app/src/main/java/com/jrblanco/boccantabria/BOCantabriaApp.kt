package com.jrblanco.boccantabria

import android.app.Application
import com.jrblanco.boccantabria.core.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class BOCantabriaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@BOCantabriaApp)
            modules(appModules)
        }
    }
}
