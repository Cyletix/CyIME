package com.kingzcheung.xime.ui.theme

import org.junit.Assert.*
import org.junit.Test

class MaterialRecipesTest {
    @Test fun ordinaryKeysAvoidGlowAndKeepTheOriginalUntouched() {
        VisualStyle.entries.forEach { style ->
            listOf(MaterialLevel.BASE, MaterialLevel.RAISED).forEach { level ->
                val p = MaterialRecipes.resolve(style, level)
                assertEquals(0f, p.glow, 0f)
            }
        }
        val original = MaterialRecipes.resolve(VisualStyle.ORIGINAL, MaterialLevel.BASE)
        assertEquals(0f, original.shade + original.tint + original.top + original.border +
            original.depth + original.facet + original.matte + original.glow, 0f)
    }

    @Test fun fourStylesHaveDifferentPrimaryTreatments() {
        val neon = MaterialRecipes.resolve(VisualStyle.NEON, MaterialLevel.BASE)
        val glass = MaterialRecipes.resolve(VisualStyle.GLASS, MaterialLevel.BASE)
        val facet = MaterialRecipes.resolve(VisualStyle.FACET, MaterialLevel.BASE)
        val frost = MaterialRecipes.resolve(VisualStyle.FROST, MaterialLevel.BASE)
        assertTrue(neon.border > glass.border && glass.border > facet.border)
        assertTrue(glass.top > neon.top && glass.top > facet.top)
        assertTrue(facet.facet > 0f && frost.facet == 0f)
        assertTrue(frost.matte > 0f && glass.matte == 0f)
        assertTrue(MaterialRecipes.resolve(VisualStyle.NEON, MaterialLevel.FLOATING).glow > 0f)
    }
}
