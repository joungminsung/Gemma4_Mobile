package com.gemma4mobile.model

import org.junit.Assert.*
import org.junit.Test

class ModelTierTest {

    @Test
    fun `RAM 3GB returns null - unsupported device`() {
        val tier = ModelTier.forDevice(ramMb = 3000)
        assertNull(tier)
    }

    @Test
    fun `RAM 4GB returns LITE`() {
        val tier = ModelTier.forDevice(ramMb = 4000)
        assertEquals(ModelTier.LITE, tier)
    }

    @Test
    fun `RAM 6GB returns STANDARD`() {
        val tier = ModelTier.forDevice(ramMb = 6000)
        assertEquals(ModelTier.STANDARD, tier)
    }

    @Test
    fun `RAM 8GB returns FULL`() {
        val tier = ModelTier.forDevice(ramMb = 8000)
        assertEquals(ModelTier.FULL, tier)
    }

    @Test
    fun `RAM 12GB returns MAX`() {
        val tier = ModelTier.forDevice(ramMb = 12000)
        assertEquals(ModelTier.MAX, tier)
    }

    @Test
    fun `RAM 16GB returns MAX`() {
        val tier = ModelTier.forDevice(ramMb = 16000)
        assertEquals(ModelTier.MAX, tier)
    }

    @Test
    fun `each tier has correct model filename`() {
        assertEquals("gemma4_ko_lite.task", ModelTier.LITE.modelFilename)
        assertEquals("gemma4_ko_standard.task", ModelTier.STANDARD.modelFilename)
        assertEquals("gemma4_ko_full.task", ModelTier.FULL.modelFilename)
        assertEquals("gemma4_ko_max.task", ModelTier.MAX.modelFilename)
    }

    @Test
    fun `each tier has increasing download size`() {
        assertTrue(ModelTier.LITE.downloadSizeMb < ModelTier.STANDARD.downloadSizeMb)
        assertTrue(ModelTier.STANDARD.downloadSizeMb < ModelTier.FULL.downloadSizeMb)
        assertTrue(ModelTier.FULL.downloadSizeMb < ModelTier.MAX.downloadSizeMb)
    }
}
