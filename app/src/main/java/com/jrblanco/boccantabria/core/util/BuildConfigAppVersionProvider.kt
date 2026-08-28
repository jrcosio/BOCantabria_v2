package com.jrblanco.boccantabria.core.util

import com.jrblanco.boccantabria.BuildConfig

class BuildConfigAppVersionProvider : AppVersionProvider {
    override val versionCode: Int = BuildConfig.VERSION_CODE
}
