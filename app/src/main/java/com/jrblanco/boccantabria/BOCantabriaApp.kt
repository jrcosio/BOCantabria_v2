package com.jrblanco.boccantabria

import android.app.Application
import android.os.Process
import com.jrblanco.boccantabria.core.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class BOCantabriaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@BOCantabriaApp)
            // WorkManager built from the graph, so the alerts worker gets its dependencies by
            // constructor. The manifest removes the default initialiser (012 research.md D-420).
            //
            // **Never in an isolated process.** The PDF viewer renders in one
            // (`androidx.pdf.service.PdfDocumentServiceImpl`), and it has no system services: initialising
            // WorkManager there throws on `ConnectivityManager` and the process dies before it renders a
            // page. The default initialiser never had this problem because a `ContentProvider` does not
            // run in isolated processes; moving the initialisation here inherited the duty of not doing
            // it there. Caught by the instrumented suite on 6 September 2026, not by any unit test.
            if (!Process.isIsolated()) workManagerFactory()
            modules(appModules)
        }
    }
}
