package com.whisper.wowreader;

import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={24,35,36}, manifest=Config.NONE)
public class GlobalTypographySettingsTest {
    private SharedPreferences prefs;

    @Before public void setup() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("global-typography-test", 0);
        prefs.edit().clear().commit();
    }

    @Test public void perBookOverridesAreIgnoredForEveryBookName() {
        String old = "book_typography_" + Integer.toHexString("one.epub".hashCode()) + "_";
        prefs.edit().putBoolean(old + "saved", true).putInt(old + "font_percent", 280)
                .putInt("epub_font", 130).commit();
        assertEquals(130, GlobalTypographySettings.load(prefs).fontPercent);
        assertEquals(130, GlobalTypographySettings.load(prefs).fontPercent);
    }

    @Test public void allSevenGlobalFieldsRoundTripIncludingWeight() {
        GlobalTypographySettings.Values v = new GlobalTypographySettings.Values();
        v.fontPercent = 220; v.fontWeight = 550; v.fontChoice = "custom:abc";
        v.publisherStyles=false; v.lineSpacing = 210; v.marginPercent = 12; v.textAlignment = "right"; v.autoSpacing = false;
        SharedPreferences.Editor editor = prefs.edit();
        GlobalTypographySettings.write(editor, v);
        editor.commit();
        GlobalTypographySettings.Values got = GlobalTypographySettings.load(prefs);
        assertEquals(220, got.fontPercent); assertEquals(600, got.fontWeight);
        assertEquals("custom:abc", got.fontChoice); assertEquals(210, got.lineSpacing);
        assertEquals(12, got.marginPercent); assertEquals("right", got.textAlignment);
        assertFalse(got.autoSpacing);
    }

    @Test public void legacyForcedAlignmentMigratesToOriginalWithoutLosingGlobalTypography() {
        prefs.edit().putString("epub_text_alignment","left").putInt("epub_font",180).putString("epub_font_choice","pyidaungsu").commit();
        GlobalTypographySettings.Values got=GlobalTypographySettings.load(prefs);
        assertFalse(got.publisherStyles);assertEquals("publisher",got.textAlignment);
        assertEquals(180,got.fontPercent);assertEquals("pyidaungsu",got.fontChoice);
    }
    @Test public void originalModeRoundTripsGlobally() {
        GlobalTypographySettings.Values original=new GlobalTypographySettings.Values();
        SharedPreferences.Editor e=prefs.edit();GlobalTypographySettings.write(e,original);e.commit();
        GlobalTypographySettings.Values got=GlobalTypographySettings.load(prefs);
        assertTrue(got.publisherStyles);assertEquals("publisher",got.textAlignment);
    }

    @Test public void defaultsClampAndLegacyKeysSurvive() {
        prefs.edit().putString("legacy_unrelated", "keep").putInt("epub_font", 999)
                .putInt("epub_font_weight", 250).putInt("epub_line_spacing", 1)
                .putInt("epub_margin", 99).putString("epub_text_alignment", "bad").commit();
        GlobalTypographySettings.Values got = GlobalTypographySettings.load(prefs);
        assertEquals(300, got.fontPercent); assertEquals(300, got.fontWeight);
        assertEquals(120, got.lineSpacing); assertEquals(14, got.marginPercent);
        assertEquals("publisher", got.textAlignment); assertEquals("keep", prefs.getString("legacy_unrelated", null));
        assertTrue(got.autoSpacing);
    }
}
