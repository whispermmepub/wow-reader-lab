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
@Config(sdk={24,35,36},manifest=Config.NONE)
public class BookTypographyStoreTest {
    private SharedPreferences prefs;
    @Before public void setup(){prefs=RuntimeEnvironment.getApplication().getSharedPreferences("typography-test",0);prefs.edit().clear().commit();}
    @Test public void legacyAutoSavedStyleIsPreservedButDoesNotOverrideGlobals() {
        String name="legacy.epub", old="book_typography_"+Integer.toHexString(name.hashCode())+"_";
        prefs.edit().putBoolean(old+"saved",true).putInt(old+"font_percent",230).commit();
        assertTrue(BookTypographyStore.hasLegacySaved(prefs,name));
        assertFalse(BookTypographyStore.hasOverride(prefs,name));
        assertEquals(140,load(name,140).fontPercent);
        assertEquals(230,prefs.getInt(old+"font_percent",0));
        assertTrue(BookTypographyStore.importLegacy(prefs,name));
        assertTrue(BookTypographyStore.hasOverride(prefs,name));
        assertEquals(230,load(name,140).fontPercent);
    }
    @Test public void globalFallbackCanBeRestoredWithoutLosingExplicitBookValues() {
        BookTypographyStore.save(prefs,"first.epub",180,"publisher",170,6,"left",true);
        assertEquals(180,load("first.epub",130).fontPercent);
        assertEquals(130,load("other.epub",130).fontPercent);
        BookTypographyStore.setEnabled(prefs,"first.epub",false);
        assertEquals(150,load("first.epub",150).fontPercent);
        assertTrue(BookTypographyStore.hasSaved(prefs,"first.epub"));
        BookTypographyStore.setEnabled(prefs,"first.epub",true);
        assertEquals(180,load("first.epub",150).fontPercent);
    }
    @Test public void javaHashCollisionCannotMixBookStyles() {
        assertEquals("Aa.epub".hashCode(),"BB.epub".hashCode());
        BookTypographyStore.save(prefs,"Aa.epub",120,"publisher",160,5,"left",true);
        BookTypographyStore.save(prefs,"BB.epub",200,"publisher",190,9,"right",false);
        assertEquals(120,load("Aa.epub",100).fontPercent);
        assertEquals(200,load("BB.epub",100).fontPercent);
        BookTypographyStore.clearOverride(prefs,"Aa.epub");
        assertEquals(100,load("Aa.epub",100).fontPercent);
        assertEquals(200,load("BB.epub",100).fontPercent);
    }
    private BookTypographyStore.Values load(String name,int font){return BookTypographyStore.load(prefs,name,font,"publisher",160,5,"justify",true);}
}
