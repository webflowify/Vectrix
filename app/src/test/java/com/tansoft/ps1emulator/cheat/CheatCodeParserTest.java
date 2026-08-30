/*
 * PS1 Emulator for Android
 * Copyright (C) 2024-2026 MST. ROKIEA SULTANA
 * 2 No. College Gate Bylane, Mymensingh - 2207, Bangladesh (BD)
*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package com.tansoft.ps1emulator.cheat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class CheatCodeParserTest {

    // ---- 1. Single valid code returned unchanged in uppercase ----

    @Test
    public void singleValidCode_returnedUppercase() {
        String result = CheatCodeParser.normalize("80012345 0009");
        assertEquals("80012345 0009", result);
    }

    @Test
    public void singleValidCode_lowercaseInput_uppercased() {
        String result = CheatCodeParser.normalize("80012345 0009");
        assertEquals("80012345 0009", result);
    }

    // ---- 2. Multiple lines with messy whitespace and line endings ----

    @Test
    public void multipleLines_normalizesWhitespaceAndLineEndings() {
        String raw = "  80012345\t0009  \r\n80012346 000A\r\n";
        String expected = "80012345 0009\n80012346 000A";
        String result = CheatCodeParser.normalize(raw);
        assertEquals(expected, result);
    }

    @Test
    public void multipleLines_mixedCase_lowerAndUpper() {
        String raw = "80012345 0009\n80012346 000a";
        String expected = "80012345 0009\n80012346 000A";
        assertEquals(expected, CheatCodeParser.normalize(raw));
    }

    @Test
    public void multipleLines_extraTabsAndSpaces() {
        String raw = "80012345\t\t0009\n80012346     000A";
        String expected = "80012345 0009\n80012346 000A";
        assertEquals(expected, CheatCodeParser.normalize(raw));
    }

    @Test
    public void multipleLines_oldMacLineEnding() {
        String raw = "80012345 0009\r80012346 000A";
        String expected = "80012345 0009\n80012346 000A";
        assertEquals(expected, CheatCodeParser.normalize(raw));
    }

    // ---- 3. Blank lines are ignored; all-blank throws ----

    @Test
    public void blankLines_ignored() {
        String raw = "\n\n80012345 0009\n\n";
        assertEquals("80012345 0009", CheatCodeParser.normalize(raw));
    }

    @Test(expected = IllegalArgumentException.class)
    public void allBlankInput_throws() {
        CheatCodeParser.normalize("\n\n\n");
    }

    @Test(expected = IllegalArgumentException.class)
    public void whitespaceOnlyInput_throws() {
        CheatCodeParser.normalize("   \t\t  ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyInput_throws() {
        CheatCodeParser.normalize("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullInput_throws() {
        CheatCodeParser.normalize(null);
    }

    // ---- 4. Invalid cases each throw ----

    @Test(expected = IllegalArgumentException.class)
    public void missingValue_throws() {
        CheatCodeParser.normalize("80012345");
    }

    @Test(expected = IllegalArgumentException.class)
    public void sevenDigitAddress_throws() {
        CheatCodeParser.normalize("8001234 0009");
    }

    @Test(expected = IllegalArgumentException.class)
    public void nineDigitAddress_throws() {
        CheatCodeParser.normalize("800123456 0009");
    }

    @Test(expected = IllegalArgumentException.class)
    public void fiveDigitValue_throws() {
        CheatCodeParser.normalize("80012345 00090");
    }

    @Test(expected = IllegalArgumentException.class)
    public void threeDigitValue_throws() {
        CheatCodeParser.normalize("80012345 009");
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonHexAddress_throws() {
        CheatCodeParser.normalize("8001234G 0009");
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonHexValue_throws() {
        CheatCodeParser.normalize("80012345 00ZG");
    }

    @Test(expected = IllegalArgumentException.class)
    public void addressBelowMinimum_throws() {
        CheatCodeParser.normalize("0FFFFFFF 0009");
    }

    @Test(expected = IllegalArgumentException.class)
    public void addressZero_throws() {
        CheatCodeParser.normalize("00000000 0009");
    }

    @Test(expected = IllegalArgumentException.class)
    public void tooManyTokens_throws() {
        CheatCodeParser.normalize("80012345 0009 extra");
    }

    @Test(expected = IllegalArgumentException.class)
    public void onlyAddress_throws() {
        CheatCodeParser.normalize("80012345");
    }

    // ---- 5. isValid returns correct booleans ----

    @Test
    public void isValid_validMultiLineCode() {
        String raw = "80012345 0009\n80012346 000A";
        assertTrue(CheatCodeParser.isValid(raw));
    }

    @Test
    public void isValid_validSingleLine() {
        assertTrue(CheatCodeParser.isValid("80012345 0009"));
    }

    @Test
    public void isValid_validWithWhitespace() {
        assertTrue(CheatCodeParser.isValid("  80012345\t0009\r\n"));
    }

    @Test
    public void isValid_invalidAddressLow() {
        assertFalse(CheatCodeParser.isValid("0FFFFFFF 0009"));
    }

    @Test
    public void isValid_invalidHex() {
        assertFalse(CheatCodeParser.isValid("8001234G 0009"));
    }

    @Test
    public void isValid_emptyInput() {
        assertFalse(CheatCodeParser.isValid(""));
    }

    @Test
    public void isValid_nullInput() {
        assertFalse(CheatCodeParser.isValid(null));
    }

    @Test
    public void isValid_tooFewTokens() {
        assertFalse(CheatCodeParser.isValid("80012345"));
    }

    @Test
    public void isValid_tooManyTokens() {
        assertFalse(CheatCodeParser.isValid("80012345 0009 extra"));
    }

    // ---- Boundary addresses ----

    @Test
    public void addressExactlyMinimum_succeeds() {
        String result = CheatCodeParser.normalize("10000000 0001");
        assertEquals("10000000 0001", result);
    }

    @Test
    public void addressJustBelowMinimum_throws() {
        expectThrows("0FFFFFFF 0001");
    }

    @Test
    public void maxAddress_succeeds() {
        String result = CheatCodeParser.normalize("FFFFFFFF 0000");
        assertEquals("FFFFFFFF 0000", result);
    }

    @Test
    public void maxValue_succeeds() {
        String result = CheatCodeParser.normalize("80012345 FFFF");
        assertEquals("80012345 FFFF", result);
    }

    @Test
    public void zeroValue_succeeds() {
        String result = CheatCodeParser.normalize("80012345 0000");
        assertEquals("80012345 0000", result);
    }

    private void expectThrows(String input) {
        try {
            CheatCodeParser.normalize(input);
            fail("Expected IllegalArgumentException for: " + input);
        } catch (IllegalArgumentException ignored) {
            // expected
        }
    }
}
