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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.meeds.analytics.model.chart.ChartAggregationLabel;
import io.meeds.analytics.model.chart.ChartAggregationValue;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregation;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregationType;

/**
 * Covers the x-axis category cell of the chart export: a single date bucket
 * becomes a real date, anything else keeps its label.
 * <p>
 * The composite case is the one worth pinning. A chart aggregated on two
 * x-axis fields at once carries a label like "1 Sep 2026 - Marketing", whose
 * bucket key belongs to only one of the two: written as a date it would
 * silently drop the second half.
 */
class AnalyticsChartCategoryCellTest {

  private static final ZoneId          PARIS      = ZoneId.of("Europe/Paris");

  /** 2026-09-10T17:45:00+02:00 */
  private static final String          TIMESTAMP  = "1789055100000";

  private XSSFWorkbook                 workbook;

  private Row                          row;

  private AnalyticsPortlet             portlet;

  private HashMap<String, CellStyle>   dateStyles;

  @BeforeEach
  void setUp() {
    workbook = new XSSFWorkbook();
    Sheet sheet = workbook.createSheet("Chart");
    row = sheet.createRow(0);
    portlet = new AnalyticsPortlet();
    dateStyles = new HashMap<>();
  }

  @AfterEach
  void tearDown() throws IOException {
    workbook.close();
  }

  private Cell cell() {
    return row.createCell(row.getPhysicalNumberOfCells());
  }

  private ChartAggregationValue dateBucket(String key) {
    AnalyticsAggregation aggregation = new AnalyticsAggregation();
    aggregation.setType(AnalyticsAggregationType.DATE);
    aggregation.setInterval("day");
    aggregation.setField("timestamp");
    return new ChartAggregationValue(aggregation, key, key);
  }

  private ChartAggregationValue termsBucket(String value) {
    AnalyticsAggregation aggregation = new AnalyticsAggregation();
    aggregation.setType(AnalyticsAggregationType.TERMS);
    aggregation.setField("spaceId");
    return new ChartAggregationValue(aggregation, value, value);
  }

  @Test
  void testASingleDateBucketIsWrittenAsARealDate() {
    Cell cell = cell();
    portlet.writeCategoryCell(cell,
                              new ChartAggregationLabel(List.of(dateBucket(TIMESTAMP)), "10 Sep 2026", "en"),
                              PARIS,
                              dateStyles);

    assertEquals(CellType.NUMERIC, cell.getCellType());
    assertTrue(DateUtil.isCellDateFormatted(cell), "The x-axis must sort chronologically, not lexicographically");
    assertEquals(2026, cell.getLocalDateTimeCellValue().getYear());
    assertEquals(10, cell.getLocalDateTimeCellValue().getDayOfMonth());
  }

  @Test
  void testACompositeCategoryKeepsItsLabel() {
    // Two x-axis fields: the bucket key covers only one of them, so a date
    // cell would drop "Marketing" from the reader's sheet
    Cell cell = cell();
    portlet.writeCategoryCell(cell,
                              new ChartAggregationLabel(List.of(dateBucket(TIMESTAMP), termsBucket("Marketing")),
                                                        "10 Sep 2026 - Marketing",
                                                        "en"),
                              PARIS,
                              dateStyles);

    assertEquals(CellType.STRING, cell.getCellType());
    assertEquals("10 Sep 2026 - Marketing", cell.getStringCellValue());
  }

  @Test
  void testANonDateCategoryKeepsItsLabel() {
    Cell cell = cell();
    portlet.writeCategoryCell(cell,
                              new ChartAggregationLabel(List.of(termsBucket("Marketing")), "Marketing", "en"),
                              PARIS,
                              dateStyles);

    assertEquals(CellType.STRING, cell.getCellType());
    assertEquals("Marketing", cell.getStringCellValue());
  }

  @Test
  void testACategoryWithoutAggregationValuesKeepsItsLabel() {
    for (List<ChartAggregationValue> values : java.util.Arrays.asList(null, List.<ChartAggregationValue> of())) {
      Cell cell = cell();
      portlet.writeCategoryCell(cell, new ChartAggregationLabel(values, "Total", "en"), PARIS, dateStyles);

      assertEquals(CellType.STRING, cell.getCellType());
      assertEquals("Total", cell.getStringCellValue());
    }
  }

  @Test
  void testABucketKeyThatIsNotAnInstantKeepsItsLabel() {
    Cell cell = cell();
    portlet.writeCategoryCell(cell,
                              new ChartAggregationLabel(List.of(dateBucket("not-a-timestamp")), "Unknown", "en"),
                              PARIS,
                              dateStyles);

    assertEquals(CellType.STRING, cell.getCellType());
    assertEquals("Unknown", cell.getStringCellValue());
  }

  @Test
  void testAnHourOfDayBucketKeepsItsLabel() {
    // The hour bucket key is an hour of day (0-23), not an instant: as a date
    // it would put 1 January 1970 in every row of an hourly export
    AnalyticsAggregation aggregation = new AnalyticsAggregation();
    aggregation.setType(AnalyticsAggregationType.DATE);
    aggregation.setInterval("hour");
    aggregation.setField("timestamp");

    Cell cell = cell();
    portlet.writeCategoryCell(cell,
                              new ChartAggregationLabel(List.of(new ChartAggregationValue(aggregation, "17", "17:00")),
                                                        "17:00",
                                                        "en"),
                              PARIS,
                              dateStyles);

    // A string cell is already proof it was not written as a date:
    // isCellDateFormatted throws on one
    assertEquals(CellType.STRING, cell.getCellType());
    assertEquals("17:00", cell.getStringCellValue());
  }

}
