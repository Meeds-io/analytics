/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
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
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.meeds.analytics.portlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregation;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregationType;

/**
 * A date written as its localized label ("1 sept. 2026") is only a picture of
 * a date to a spreadsheet, and no cell formatting recovers it once it is
 * there. These tests read the workbook back through POI itself — the engine
 * that has to agree — rather than asserting on what was passed in.
 */
class AnalyticsExcelDateCellTest {

  private static final ZoneId  PARIS = ZoneId.of("Europe/Paris");

  /**
   * The epoch-millis key a daily bucket carries for the 1st of September
   * 2026 as seen from Paris — the form Elasticsearch returns for a
   * date_histogram over the "timestamp" field, which is mapped as
   * epoch_millis.
   */
  private static final long    SEPTEMBER_1ST_IN_PARIS = LocalDateTime.of(2026, 9, 1, 0, 0)
                                                                     .atZone(PARIS)
                                                                     .toInstant()
                                                                     .toEpochMilli();

  private AnalyticsPortlet     portlet;

  private XSSFWorkbook         workbook;

  private Cell                 cell;

  private Map<String, CellStyle> styles;

  @BeforeEach
  void setUp() {
    // Only writeDateCell is exercised: it touches the cell and the workbook,
    // never any portlet or container state
    portlet = new AnalyticsPortlet();
    workbook = new XSSFWorkbook();
    cell = workbook.createSheet().createRow(0).createCell(0);
    styles = new HashMap<>();
  }

  @AfterEach
  void tearDown() throws IOException {
    workbook.close();
  }

  @Test
  void testDailyBucketIsWrittenAsARealDate() {
    boolean written = portlet.writeDateCell(cell,
                                            aggregation(AnalyticsAggregation.DAY_INTERVAL),
                                            String.valueOf(SEPTEMBER_1ST_IN_PARIS),
                                            PARIS,
                                            styles);

    assertTrue(written, "A daily bucket must be exported as a date, not as its label");
    assertEquals(CellType.NUMERIC, cell.getCellType(), "A date cell is numeric underneath: a string cell cannot be sorted as a date");
    assertTrue(DateUtil.isCellDateFormatted(cell), "The cell must carry a date format, otherwise Excel shows the raw serial number");
    assertEquals(LocalDateTime.of(2026, 9, 1, 0, 0), cell.getLocalDateTimeCellValue());
  }

  @Test
  void testBucketIsWrittenInTheQueriedTimeZone() {
    // Same instant, read from a zone a day behind: the exported date must be
    // the one the chart draws, not the one the server's own zone would give
    portlet.writeDateCell(cell,
                          aggregation(AnalyticsAggregation.DAY_INTERVAL),
                          String.valueOf(SEPTEMBER_1ST_IN_PARIS),
                          ZoneId.of("Pacific/Honolulu"),
                          styles);

    assertEquals(LocalDateTime.of(2026, 8, 31, 12, 0), cell.getLocalDateTimeCellValue());
  }

  @Test
  void testHourOfDayBucketKeepsItsLabel() {
    // The hour interval buckets by hour of day (0-23) cumulated over the
    // whole period: the key is an hour number, and 14 read as an instant
    // would export as 1 January 1970
    boolean written = portlet.writeDateCell(cell, aggregation(AnalyticsAggregation.HOUR_INTERVAL), "14", PARIS, styles);

    assertFalse(written, "An hour-of-day bucket is not an instant and must fall back to its label");
  }

  @Test
  void testIntervalsWithoutASpreadsheetFormatKeepTheirLabel() {
    assertFalse(portlet.writeDateCell(cell,
                                      aggregation(AnalyticsAggregation.QUARTER_INTERVAL),
                                      String.valueOf(SEPTEMBER_1ST_IN_PARIS),
                                      PARIS,
                                      styles),
                "A quarter has no faithful spreadsheet format: exporting its first day would lose 'Q3 2026'");
    assertFalse(portlet.writeDateCell(cell,
                                      aggregation(AnalyticsAggregation.WEEK_INTERVAL),
                                      String.valueOf(SEPTEMBER_1ST_IN_PARIS),
                                      PARIS,
                                      styles),
                "An ISO week has no faithful spreadsheet format either");
  }

  @Test
  void testNonTimestampKeyKeepsItsLabel() {
    boolean written = portlet.writeDateCell(cell, aggregation(AnalyticsAggregation.DAY_INTERVAL), "not-a-timestamp", PARIS, styles);

    assertFalse(written, "A key that is not epoch millis must fall back to its label instead of failing the export");
  }

  @Test
  void testOneStyleIsSharedByEveryCellOfTheSameFormat() {
    // A workbook holds a bounded number of cell styles, and an export runs
    // up to 5000 rows: one style per cell would bloat the file and can hit
    // that ceiling
    AnalyticsAggregation aggregation = aggregation(AnalyticsAggregation.DAY_INTERVAL);
    for (int row = 0; row < 50; row++) {
      Cell rowCell = workbook.getSheetAt(0).createRow(row + 1).createCell(0);
      portlet.writeDateCell(rowCell, aggregation, String.valueOf(SEPTEMBER_1ST_IN_PARIS), PARIS, styles);
    }

    assertEquals(1, styles.size(), "Every daily bucket must reuse the one style created for its format");
  }

  private AnalyticsAggregation aggregation(String interval) {
    AnalyticsAggregation aggregation = new AnalyticsAggregation();
    aggregation.setType(AnalyticsAggregationType.DATE);
    aggregation.setField("timestamp");
    aggregation.setInterval(interval);
    return aggregation;
  }

}
