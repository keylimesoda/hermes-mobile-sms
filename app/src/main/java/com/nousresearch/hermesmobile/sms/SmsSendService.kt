package com.nousresearch.hermesmobile.sms

/** Narrow boundary around Android SMS APIs so policy can be tested without a device or Robolectric. */
interface SmsPlatform {
    fun hasSmsPermission(): Boolean
    fun hasTelephonyFeature(): Boolean
    fun divideMessage(message: String): List<String>
    fun sendMultipart(recipient: String, parts: List<String>)
}

sealed interface SmsSendResult {
    data class Queued(val partCount: Int) : SmsSendResult
    data object InvalidRecipient : SmsSendResult
    data object EmptyMessage : SmsSendResult
    data object UnsupportedDevice : SmsSendResult
    data object PermissionRequired : SmsSendResult
    data object MessageTooLong : SmsSendResult
    data object SendFailed : SmsSendResult
}

class SmsSendService(private val platform: SmsPlatform) {
    fun send(recipient: String, message: String): SmsSendResult {
        val address = SmsAddress.normalize(recipient) ?: return SmsSendResult.InvalidRecipient
        if (message.isBlank()) return SmsSendResult.EmptyMessage
        if (message.length > MAX_MESSAGE_CHARACTERS) return SmsSendResult.MessageTooLong
        if (!platform.hasTelephonyFeature()) return SmsSendResult.UnsupportedDevice
        if (!platform.hasSmsPermission()) return SmsSendResult.PermissionRequired

        return try {
            val parts = platform.divideMessage(message)
            if (parts.isEmpty()) return SmsSendResult.SendFailed
            platform.sendMultipart(address, parts)
            SmsSendResult.Queued(parts.size)
        } catch (_: SecurityException) {
            // Permission may be revoked between the check and SmsManager call.
            SmsSendResult.PermissionRequired
        } catch (_: RuntimeException) {
            SmsSendResult.SendFailed
        }
    }

    private companion object {
        const val MAX_MESSAGE_CHARACTERS = 10_000
    }
}
