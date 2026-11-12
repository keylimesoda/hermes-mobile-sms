package com.nousresearch.hermesmobile.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSendServiceTest {
    private class FakeSmsPlatform(
        var permission: Boolean = true,
        var telephony: Boolean = true,
        var parts: List<String> = listOf("hello")
    ) : SmsPlatform {
        var recipient: String? = null
        var sentParts: List<String>? = null
        var sendFailure: RuntimeException? = null
        override fun hasSmsPermission() = permission
        override fun hasTelephonyFeature() = telephony
        override fun divideMessage(message: String) = parts
        override fun sendMultipart(recipient: String, parts: List<String>) {
            sendFailure?.let { throw it }
            this.recipient = recipient
            this.sentParts = parts
        }
    }

    @Test fun sendsNormalizedNumberAndAllMessageParts() {
        val platform = FakeSmsPlatform(parts = listOf("part one", "part two"))
        val result = SmsSendService(platform).send("+1 (415) 555-2671", "hello world")
        assertEquals(SmsSendResult.Queued(partCount = 2), result)
        assertEquals("+14155552671", platform.recipient)
        assertEquals(listOf("part one", "part two"), platform.sentParts)
    }

    @Test fun rejectsInvalidRecipientWithoutSending() {
        val platform = FakeSmsPlatform()
        assertEquals(SmsSendResult.InvalidRecipient, SmsSendService(platform).send("4155552671", "hello"))
        assertFalse(platform.sentParts != null)
    }

    @Test fun refusesSendingWithoutTelephony() {
        val platform = FakeSmsPlatform(telephony = false)
        assertEquals(SmsSendResult.UnsupportedDevice, SmsSendService(platform).send("+14155552671", "hello"))
        assertTrue(platform.sentParts == null)
    }

    @Test fun refusesSendingWithoutPermission() {
        val platform = FakeSmsPlatform(permission = false)
        assertEquals(SmsSendResult.PermissionRequired, SmsSendService(platform).send("+14155552671", "hello"))
        assertTrue(platform.sentParts == null)
    }

    @Test fun rejectsBlankMessageWithoutSending() {
        val platform = FakeSmsPlatform()
        assertEquals(SmsSendResult.EmptyMessage, SmsSendService(platform).send("+14155552671", "  "))
        assertTrue(platform.sentParts == null)
    }

    @Test fun rejectsOversizedMessageWithoutSending() {
        val platform = FakeSmsPlatform()
        assertEquals(SmsSendResult.MessageTooLong, SmsSendService(platform).send("+14155552671", "x".repeat(10_001)))
        assertTrue(platform.sentParts == null)
    }

    @Test fun reportsPermissionRevokedDuringSend() {
        val platform = FakeSmsPlatform().apply { sendFailure = SecurityException() }
        assertEquals(SmsSendResult.PermissionRequired, SmsSendService(platform).send("+14155552671", "hello"))
    }

    @Test fun reportsPlatformFailureWithoutLeakingException() {
        val platform = FakeSmsPlatform().apply { sendFailure = IllegalStateException("device-private detail") }
        assertEquals(SmsSendResult.SendFailed, SmsSendService(platform).send("+14155552671", "hello"))
    }
}
