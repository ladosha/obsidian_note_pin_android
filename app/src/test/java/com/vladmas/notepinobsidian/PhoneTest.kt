package com.vladmas.notepinobsidian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneTest {
    @Test
    fun xiaomiSystem() {
        assertEquals("HyperOS 2", Phone.xiaomiSystem("OS2.0.201.0.VOLMIXM"))
        assertEquals("HyperOS 1", Phone.xiaomiSystem("OS1.0.5.0.UNCMIXM"))
        assertEquals("HyperOS 2.1", Phone.xiaomiSystem("OS2.1.3.0.VOCCNXM"))
        assertEquals("MIUI 14", Phone.xiaomiSystem("V14.0.4.0.TMCMIXM"))
        assertEquals("MIUI 12.5", Phone.xiaomiSystem("V12.5.1.0.RKHEUXM"))
        assertEquals("MIUI", Phone.xiaomiSystem("eng.builder.20250101"))
    }

    @Test
    fun displayName() {
        assertEquals("POCO F7", Phone.displayName("POCO F7", null, "Xiaomi", "25053PC47G"))
        assertEquals("Galaxy S24 Ultra", Phone.displayName("", "Galaxy S24 Ultra", "samsung", "SM-S928B"))
        assertEquals("Google Pixel 9", Phone.displayName(null, null, "Google", "Pixel 9"))
        assertEquals("OnePlus 12", Phone.displayName(null, " ", "OnePlus", "OnePlus 12"))
    }

    @Test
    fun oneUi() {
        assertEquals(6.1, Phone.oneUi(60100)!!, 0.0)
        assertEquals(7.0, Phone.oneUi(70000)!!, 0.0)
        assertEquals(8.5, Phone.oneUi(80500)!!, 0.0)
        assertNull(Phone.oneUi(0))
    }
}
