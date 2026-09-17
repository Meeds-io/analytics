/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2026 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */
package io.meeds.analytics.portlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Locale;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * The export sheet name is built from an admin-typed, possibly multilingual
 * title. POI refuses several characters in a sheet name, so the raw title
 * cannot reach {@code createSheet}: these tests pin the resolution of a
 * translations-JSON title and the sanitising of the sheet name against POI
 * itself.
 */
class AnalyticsExportTitleTest {

  private static final String FORBIDDEN = ":/\\?*[]";

  @Test
  void aPlainTitleIsReturnedUnchanged() {
    assertEquals("Top 10 spaces", AbstractAnalyticsPortlet.resolveExportTitle("Top 10 spaces", Locale.FRENCH));
    assertEquals("", AbstractAnalyticsPortlet.resolveExportTitle("  ", Locale.FRENCH));
    assertEquals("", AbstractAnalyticsPortlet.resolveExportTitle(null, null));
  }

  @Test
  void aTranslationsJsonTitleResolvesToTheRequestLocaleThenEnglishThenAnyValue() {
    String title = "{\"en\":\"Adoption\",\"fr\":\"Adoption des espaces\"}";
    assertEquals("Adoption des espaces", AbstractAnalyticsPortlet.resolveExportTitle(title, Locale.FRENCH));
    assertEquals("Adoption", AbstractAnalyticsPortlet.resolveExportTitle(title, Locale.GERMAN));
    assertEquals("Adoption", AbstractAnalyticsPortlet.resolveExportTitle(title, null));
    assertEquals("Uso", AbstractAnalyticsPortlet.resolveExportTitle("{\"es\":\"Uso\"}", Locale.FRENCH));
    assertEquals("", AbstractAnalyticsPortlet.resolveExportTitle("{\"en\":\"\"}", Locale.ENGLISH));
  }

  @Test
  void aBracedTitleThatIsNotJsonStaysLiteral() {
    assertEquals("{not json}", AbstractAnalyticsPortlet.resolveExportTitle("{not json}", Locale.ENGLISH));
  }

  @Test
  void theRawTitleWouldMakePoiThrow() throws IOException {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      assertThrows(IllegalArgumentException.class, () -> workbook.createSheet("Top 10: spaces"));
      assertThrows(IllegalArgumentException.class, () -> workbook.createSheet("Connexions / jour"));
    }
  }

  @Test
  void theSafeSheetNameIsAcceptedByPoiAndKeepsTheReadableText() throws IOException {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      for (String title : new String[] { "Top 10: spaces", "Connexions / jour", "[Q3] usage?", "'quoted'",
                                          "{\"en\":\"Adoption\",\"fr\":\"Adoption des espaces\"}" }) {
        String name = AbstractAnalyticsPortlet.safeSheetName(AbstractAnalyticsPortlet.resolveExportTitle(title, Locale.FRENCH),
                                                              "Table");
        assertTrue(name.length() <= 31, name);
        for (char c : FORBIDDEN.toCharArray()) {
          assertFalse(name.indexOf(c) >= 0, "'" + c + "' left in " + name);
        }
        workbook.createSheet(name); // must not throw
      }
      assertEquals("Adoption des espaces", workbook.getSheetName(4));
      assertEquals("Top 10  spaces", workbook.getSheetName(0));
    }
  }

  @Test
  void aBlankTitleFallsBackAndALongOneIsCutToThirtyOne() {
    assertEquals("Table", AbstractAnalyticsPortlet.safeSheetName("", "Table"));
    assertEquals("Table", AbstractAnalyticsPortlet.safeSheetName(null, "Table"));
    assertEquals(31, AbstractAnalyticsPortlet.safeSheetName("a".repeat(40), "Table").length());
  }
}
