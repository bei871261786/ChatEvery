package river.chat.business_user.config

import android.app.Application
import android.content.Context
import android.widget.Toast
import river.chat.lib_core.app.AppLifeCycles
import river.chat.lib_core.utils.exts.isMainProcess
import river.chat.lib_core.utils.longan.toast

class UserLifeCyclesImpl : AppLifeCycles {
    override fun attachBaseContext(application: Application, base: Context) {}
    override fun onAppRequired(application: Application) {}
    override fun onCreate(application: Application) {
        if (application.isMainProcess()) {
            "UserLifeCyclesImpl onCreate".toast()
        }
    }

    override fun onTerminate(application: Application) {
        "UserLifeCyclesImpl onTerminate".toast()
    }
}