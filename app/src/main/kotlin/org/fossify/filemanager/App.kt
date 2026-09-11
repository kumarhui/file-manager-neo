package org.fossify.filemanager


import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.github.ajalt.reprint.core.Reprint
import org.fossify.commons.FossifyApp

class App : FossifyApp() {
    override val isAppLockFeatureAvailable = true

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        Reprint.initialize(this)
    }
}
