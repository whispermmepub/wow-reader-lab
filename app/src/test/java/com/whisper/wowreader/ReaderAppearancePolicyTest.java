package com.whisper.wowreader;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReaderAppearancePolicyTest {
    @Test public void legacyOrGlobalScopeNeverActivatesBookOverride() {
        assertTrue(ReaderAppearancePolicy.usesBookOverride(ReaderAppearancePolicy.THIS_BOOK, true, true));
        assertFalse(ReaderAppearancePolicy.usesBookOverride(ReaderAppearancePolicy.ALL_BOOKS, true, true));
        assertFalse(ReaderAppearancePolicy.usesBookOverride(ReaderAppearancePolicy.THIS_BOOK, false, true));
        assertFalse(ReaderAppearancePolicy.usesBookOverride(ReaderAppearancePolicy.THIS_BOOK, true, false));
    }

    @Test public void customColorsRequireSixDigitRgbHex() {
        assertTrue(ReaderAppearancePolicy.safeHex("#aAbB09"));
        assertEquals("#AABB09", ReaderAppearancePolicy.normalizedHex(" #aAbB09 "));
        assertFalse(ReaderAppearancePolicy.safeHex("#000"));
        assertFalse(ReaderAppearancePolicy.safeHex("#GGGGGG"));
        assertNull(ReaderAppearancePolicy.normalizedHex(null));
    }
}
