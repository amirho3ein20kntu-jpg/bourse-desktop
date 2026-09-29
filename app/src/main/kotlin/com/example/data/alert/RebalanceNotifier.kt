package com.example.data.alert

import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.image.BufferedImage

/** اعلان ویندوز از طریق System Tray. */
object RebalanceNotifier {
    private var tray: TrayIcon? = null

    @Synchronized
    fun show(title: String, body: String): Boolean {
        if (!SystemTray.isSupported()) return false
        val icon = tray ?: TrayIcon(BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), "PCMR").also {
            it.isImageAutoSize = true
            SystemTray.getSystemTray().add(it)
            tray = it
        }
        icon.displayMessage(title, body, TrayIcon.MessageType.INFO)
        return true
    }
}
