package com.nousresearch.hermesmobile.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

/** Concrete device boundary. The caller must first obtain SEND_SMS at runtime. */
class AndroidSmsPlatform(context: Context) : SmsPlatform {
    private val appContext = context.applicationContext

    override fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    override fun hasTelephonyFeature(): Boolean =
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    private fun smsManager(): SmsManager =
        checkNotNull(appContext.getSystemService(SmsManager::class.java)) { "SMS service unavailable" }

    override fun divideMessage(message: String): List<String> = smsManager().divideMessage(message)

    override fun sendMultipart(recipient: String, parts: List<String>) {
        smsManager().sendMultipartTextMessage(recipient, null, ArrayList(parts), null, null)
    }
}
