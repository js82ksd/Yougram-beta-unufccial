package app.yougram

import android.app.Application
import app.yougram.core.di.AppContainer

class YougramApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Нативный tgcalls читает контекст из этого поля.
        org.telegram.messenger.ApplicationLoader.applicationContext = applicationContext
        // Служебный процесс перезапуска не должен поднимать TDLib: база аккаунта занята основным процессом.
        if (getProcessName().endsWith(":restart")) return
        container = AppContainer(this)
        container.start()
    }
}