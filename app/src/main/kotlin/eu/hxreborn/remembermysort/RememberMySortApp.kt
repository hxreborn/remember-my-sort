package eu.hxreborn.remembermysort

import android.app.Application
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArrayList

class RememberMySortApp : Application() {
    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(
            object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(xposedService: XposedService) {
                    service = xposedService
                    listeners.forEach { it.onServiceBind(xposedService) }
                }

                override fun onServiceDied(xposedService: XposedService) {
                    service = null
                    listeners.forEach { it.onServiceDied(xposedService) }
                }
            },
        )
    }

    companion object {
        var service: XposedService? = null
            private set

        private val listeners = CopyOnWriteArrayList<XposedServiceHelper.OnServiceListener>()

        fun addServiceListener(listener: XposedServiceHelper.OnServiceListener) {
            listeners.add(listener)
            service?.let { listener.onServiceBind(it) }
        }

        fun removeServiceListener(listener: XposedServiceHelper.OnServiceListener) {
            listeners.remove(listener)
        }
    }
}
