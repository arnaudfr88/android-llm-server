package de.cyclenerd.android.llm.server.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ModelValidator.
 */
class ModelValidatorTest {
    @Test
    fun `validateModel returns Invalid for non-existent file`() {
        val result = ModelValidator.validateModel("/nonexistent/model.litertlm")
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `extractMetadata identifies Gemma models`() {
        val metadata = ModelValidator.extractMetadata("gemma-4-e2b-it.litertlm")

        assertEquals("Gemma", metadata.modelFamily)
        assertEquals("4-bit (E2B)", metadata.quantization)
        assertTrue(metadata.isInstructTuned)
    }

    @Test
    fun `extractMetadata identifies Phi models`() {
        val metadata = ModelValidator.extractMetadata("phi-3-mini-instruct.litertlm")

        assertEquals("Phi", metadata.modelFamily)
        assertTrue(metadata.isInstructTuned)
    }

    @Test
    fun `extractMetadata handles unknown models`() {
        val metadata = ModelValidator.extractMetadata("unknown-model.litertlm")

        assertEquals("Unknown", metadata.modelFamily)
        assertFalse(metadata.isInstructTuned)
    }
}
