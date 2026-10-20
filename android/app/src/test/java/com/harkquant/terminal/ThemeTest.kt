package com.harkquant.terminal

import com.harkquant.terminal.ui.theme.Form
import com.harkquant.terminal.ui.theme.Themes
import com.harkquant.terminal.ui.theme.css
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test fun twentyThemesFiveFormsFourEach() {
        assertEquals(20, Themes.all.size)
        assertEquals((1..20).toList(), Themes.all.map { it.id })
        Form.entries.forEach { f -> assertEquals(4, Themes.all.count { it.form == f }) }
        assertEquals("Onyx Lime", Themes.byId(17).name)
        assertEquals(17, Themes.byId(99).id) // fallback = default theme
    }

    @Test fun cssColours() {
        val c = css("rgba(255,255,255,.07)")
        assertEquals(1f, c.red, 0.001f); assertEquals(0.07f, c.alpha, 0.01f)
        assertEquals(css("#000000"), css("#000"))
        assertEquals(0f, css("transparent").alpha, 0f)
    }

    @Test fun gradientsAndLightFlags() {
        assertTrue(Themes.byId(5).palette.bgGradient!!.size == 2)
        assertTrue(Themes.byId(4).palette.isLight)
        assertTrue(Themes.byId(19).palette.isLight)
        assertTrue(!Themes.byId(17).palette.isLight)
        assertTrue(!Themes.byId(12).palette.isLight)
    }
}
