package sk.uss.isac.chat.mobile.app

import android.app.Application

class IsacChatMobileApplication : Application() {
    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        appGraph = AppGraph(applicationContext)
    }
}

