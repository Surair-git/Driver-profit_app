package com.surair.driverprofit

import android.app.Application
import com.surair.driverprofit.data.Repository
import com.surair.driverprofit.tracking.VehicleDetection
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        repo = Repository(File(filesDir, "driver-profit.json"))
        VehicleDetection.sync(this)
    }

    companion object {
        lateinit var repo: Repository
            private set
    }
}
