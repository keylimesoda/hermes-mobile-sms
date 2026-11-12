package com.nousresearch.hermesmobile.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsAddressTest {
    @Test fun normalizesCommonInternationalFormatting() {
        assertEquals("+14155552671", SmsAddress.normalize("+1 (415) 555-2671"))
    }

    @Test fun acceptsE164AtMaximumLength() {
        assertEquals("+123456789012345", SmsAddress.normalize("+123456789012345"))
    }

    @Test fun rejectsNumbersOutsideE164DigitBounds() {
        assertNull(SmsAddress.normalize("+"))
        assertNull(SmsAddress.normalize("+1234567890123456"))
    }

    @Test fun rejectsMisplacedOrRepeatedInternationalPrefix() {
        assertNull(SmsAddress.normalize("++14155552671"))
        assertNull(SmsAddress.normalize("+1+4155552671"))
    }

    @Test fun rejectsExtensionsAndLettersRatherThanGuessing() {
        assertNull(SmsAddress.normalize("+1 415 555 2671 ext 9"))
        assertNull(SmsAddress.normalize("call me at 4155552671"))
    }

    @Test fun rejectsMissingInternationalPrefix() {
        assertNull(SmsAddress.normalize("415-555-2671"))
    }
}
