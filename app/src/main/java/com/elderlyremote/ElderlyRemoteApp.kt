package com.elderlyremote

import android.app.Application
import com.elderlyremote.data.ConfigRepository

class ElderlyRemoteApp : Application() {

    /** Singleton config repository accessible from ViewModels via the Application reference. */
    val configRepository: ConfigRepository by lazy { ConfigRepository(this) }
}
