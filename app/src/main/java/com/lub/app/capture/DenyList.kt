package com.lub.app.capture

import com.lub.app.data.SettingsStore
import kotlinx.coroutines.flow.first

object DenyList {

    suspend fun isDenied(settingsStore: SettingsStore, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val deniedList = settingsStore.denyListFlow.first()
        return deniedList.any { deniedPkg ->
            packageName.equals(deniedPkg, ignoreCase = true) ||
            packageName.startsWith("$deniedPkg.")
        }
    }
}
