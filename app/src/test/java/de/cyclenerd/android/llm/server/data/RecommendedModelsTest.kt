package de.cyclenerd.android.llm.server.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for RecommendedModels.
 */
class RecommendedModelsTest {
    @Test
    fun `RECOMMENDED_MODELS is not empty`() {
        assertTrue(RECOMMENDED_MODELS.isNotEmpty())
    }

    @Test
    fun `all recommended models have valid data`() {
        RECOMMENDED_MODELS.forEach { model ->
            assertTrue("Name should not be blank", model.name.isNotBlank())
            assertTrue("Description should not be blank", model.description.isNotBlank())
            assertTrue("Size should be positive", model.sizeMb > 0)
            assertTrue("URL should not be blank", model.downloadUrl.isNotBlank())
            assertTrue("RAM requirement should be positive", model.minRamGb > 0)
            assertTrue("SHA256 should not be blank if provided", model.sha256 == null || model.sha256.isNotBlank())
        }
    }

    @Test
    fun `findRecommendedModel returns correct model`() {
        val model = findRecommendedModel("Google DeepMind / Gemma 4 E2B")
        assertNotNull(model)
        assertEquals(2588, model?.sizeMb)
    }

    @Test
    fun `fileName extracts correctly without query parameters`() {
        RECOMMENDED_MODELS.forEach { model ->
            assertFalse("Filename should not contain ?", model.fileName.contains("?"))
            assertTrue("Filename should end with .litertlm", model.fileName.endsWith(".litertlm"))
        }
    }

    @Test
    fun `getRecommendedModels returns all models`() {
        val models = getRecommendedModels()

        assertTrue("Should return all models", models.isNotEmpty())
        assertEquals("Should match RECOMMENDED_MODELS size", RECOMMENDED_MODELS.size, models.size)

        // Verify models are not filtered by RAM
        val hasHighRamModel = models.any { it.minRamGb > 4 }
        assertTrue("Should include models requiring more than 4GB RAM", hasHighRamModel || models.isEmpty())
    }
}
