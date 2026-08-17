package com.sousoulab.einklauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTextSizePolicyTest {
    @Test
    fun `normalize preserves legacy sizes inside the supported range`() {
        assertEquals(50, HomeTextSizePolicy.MAX_SP)
        assertEquals(HomeTextSizePolicy.MIN_SP, HomeTextSizePolicy.normalize(Int.MIN_VALUE))
        assertEquals(24, HomeTextSizePolicy.normalize(24))
        assertEquals(HomeTextSizePolicy.MAX_SP, HomeTextSizePolicy.normalize(Int.MAX_VALUE))
    }

    @Test
    fun `decrease keeps the legacy step and clamps at the minimum`() {
        assertEquals(21, HomeTextSizePolicy.decrease(HomeTextSizePolicy.DEFAULT_SP))
        assertEquals(HomeTextSizePolicy.MIN_SP, HomeTextSizePolicy.decrease(19))
        assertEquals(HomeTextSizePolicy.MIN_SP, HomeTextSizePolicy.decrease(Int.MIN_VALUE))
    }

    @Test
    fun `increase keeps the legacy step and clamps at the maximum`() {
        assertEquals(25, HomeTextSizePolicy.increase(HomeTextSizePolicy.DEFAULT_SP))
        assertEquals(HomeTextSizePolicy.MAX_SP, HomeTextSizePolicy.increase(49))
        assertEquals(HomeTextSizePolicy.MAX_SP, HomeTextSizePolicy.increase(Int.MAX_VALUE))
    }
}
