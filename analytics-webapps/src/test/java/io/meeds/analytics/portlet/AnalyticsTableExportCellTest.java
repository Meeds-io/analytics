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
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.space.model.Space;

import io.meeds.analytics.model.chart.TableColumnItemValue;
import io.meeds.analytics.model.filter.AnalyticsTableColumnAggregation;
import io.meeds.analytics.model.filter.AnalyticsTableColumnFilter;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregation;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregationType;
import io.meeds.analytics.portlet.AnalyticsTablePortlet.ExportFormatting;

/**
 * Covers how a table cell is exported: which columns are treated as dates,
 * and what a value becomes in the spreadsheet.
 * <p>
 * Every assertion reads the value back off the written cell rather than
 * checking what was passed in, so a cell written as text where a date was
 * meant fails here - that is the whole defect these tests pin: an instant
 * written as a plain number reaches the reader as 1.75941E+12.
 */
class AnalyticsTableExportCellTest {

  private static final ZoneId      PARIS = ZoneId.of("Europe/Paris");

  private XSSFWorkbook             workbook;

  private Row                      row;

  private AnalyticsTablePortlet    portlet;

  private ExportFormatting         formatting;

  @BeforeEach
  void setUp() {
    workbook = new XSSFWorkbook();
    Sheet sheet = workbook.createSheet("Table");
    row = sheet.createRow(0);
    portlet = new AnalyticsTablePortlet();
    formatting = new ExportFormatting(PARIS, "en", new HashMap<>(), Set.of("lastLoginTime"));
  }

  @AfterEach
  void tearDown() throws IOException {
    workbook.close();
  }

  private Cell cell() {
    return row.createCell(row.getPhysicalNumberOfCells());
  }

  private AnalyticsTableColumnFilter column(String dataType, String aggregationField) {
    return column(dataType, aggregationField, AnalyticsAggregationType.MAX);
  }

  private AnalyticsTableColumnFilter column(String dataType, String aggregationField, AnalyticsAggregationType type) {
    AnalyticsTableColumnFilter columnFilter = new AnalyticsTableColumnFilter();
    columnFilter.setDataType(dataType);
    if (aggregationField != null || type != null) {
      AnalyticsAggregation aggregation = new AnalyticsAggregation();
      aggregation.setType(type);
      aggregation.setField(aggregationField);
      AnalyticsTableColumnAggregation columnAggregation = new AnalyticsTableColumnAggregation();
      columnAggregation.setAggregation(aggregation);
      columnFilter.setValueAggregation(columnAggregation);
    }
    return columnFilter;
  }

  @Test
  void testColumnRenderedAsADateIsADateColumn() {
    // The signal the table itself renders from: dataType == "date" is what
    // makes AnalyticsTableCellValue display a <date-format>
    assertTrue(portlet.isDateColumn(column("date", null), formatting));
    assertTrue(portlet.isDateColumn(column("DATE", null), formatting));
  }

  @Test
  void testDateFieldMappingIsTheFallbackWhenNoDataTypeWasSaved() {
    // A column saved before the data type was recorded still exports as a
    // date, through the Elasticsearch mapping
    assertTrue(portlet.isDateColumn(column(null, "lastLoginTime"), formatting));
    assertTrue(portlet.isDateColumn(column(null, "lastLoginTime.keyword"), formatting));
  }

  @Test
  void testACountOverADateFieldIsNotADateColumn() {
    // A CARDINALITY or COUNT over a date field satisfies both date signals -
    // the column's dataType comes from the field mapping - but its value is
    // a number of items. Treated as an instant, "28" exports as 28ms after
    // 1 January 1970; before the date export existed it was the number 28.
    for (AnalyticsAggregationType counting : new AnalyticsAggregationType[] {AnalyticsAggregationType.CARDINALITY,
                                                                            AnalyticsAggregationType.COUNT,
                                                                            AnalyticsAggregationType.TERMS,
                                                                            AnalyticsAggregationType.GROUP_BY}) {
      assertFalse(portlet.isDateColumn(column("date", "lastLoginTime", counting), formatting),
                  counting + " over a date field counts items, it does not produce an instant");
    }
    // the same field with an aggregation that does return an instant
    assertTrue(portlet.isDateColumn(column("date", "lastLoginTime", AnalyticsAggregationType.MAX), formatting));
  }

  @Test
  void testACountOverADateFieldKeepsItsNumber() {
    Cell cell = cell();
    AnalyticsTableColumnFilter countColumn = column("date", "lastLoginTime", AnalyticsAggregationType.CARDINALITY);
    portlet.writeValue(cell, "28", portlet.isDateColumn(countColumn, formatting), formatting);

    assertEquals(CellType.NUMERIC, cell.getCellType());
    assertFalse(DateUtil.isCellDateFormatted(cell), "A distinct-value count must not become 1 January 1970");
    assertEquals(28d, cell.getNumericCellValue());
  }

  @Test
  void testNonDateColumnIsNotADateColumn() {
    assertFalse(portlet.isDateColumn(column("long", "activitiesCount"), formatting));
    assertFalse(portlet.isDateColumn(column(null, null), formatting));
  }

  @Test
  void testDateColumnValueIsWrittenAsARealDate() {
    // 2026-09-10T17:45:00+02:00
    Cell cell = cell();
    portlet.writeValue(cell, "1789055100000", true, formatting);

    assertEquals(CellType.NUMERIC, cell.getCellType(), "An instant must be a numeric date cell, not text");
    assertTrue(DateUtil.isCellDateFormatted(cell), "The cell must carry a date format, otherwise Excel shows 1.78940E+12");
    assertEquals(2026, cell.getLocalDateTimeCellValue().getYear());
    assertEquals(9, cell.getLocalDateTimeCellValue().getMonthValue());
    assertEquals(10, cell.getLocalDateTimeCellValue().getDayOfMonth());
    assertEquals(17, cell.getLocalDateTimeCellValue().getHour(), "Written in the queried zone, not the server's");
    assertEquals(45, cell.getLocalDateTimeCellValue().getMinute());
  }

  @Test
  void testMissingValueIsAnEmptyCellNotTheWordNull() {
    // Elasticsearch returns nothing for a user who never connected
    for (String missing : new String[] {"null", "", "   "}) {
      Cell cell = cell();
      portlet.writeValue(cell, missing, true, formatting);
      assertEquals(CellType.STRING, cell.getCellType());
      assertEquals("", cell.getStringCellValue(), "'" + missing + "' must not reach the reader's spreadsheet");
    }
  }

  @Test
  void testNumericValueOfANonDateColumnStaysANumber() {
    Cell cell = cell();
    portlet.writeValue(cell, "28", false, formatting);

    assertEquals(CellType.NUMERIC, cell.getCellType());
    assertFalse(DateUtil.isCellDateFormatted(cell), "A plain count must not be formatted as a date");
    assertEquals(28d, cell.getNumericCellValue());
  }

  @Test
  void testNonNumericValueStaysText() {
    Cell cell = cell();
    portlet.writeValue(cell, "[contents]", false, formatting);

    assertEquals(CellType.STRING, cell.getCellType());
    assertEquals("[contents]", cell.getStringCellValue());
  }

  @Test
  void testDateColumnHoldingSomethingThatIsNotAnInstantFallsBack() {
    // A date column whose value is not epoch millis must not be dropped: it
    // falls back to the plain value rather than exporting an empty cell
    Cell cell = cell();
    portlet.writeValue(cell, "not-a-date", true, formatting);

    assertEquals(CellType.STRING, cell.getCellType());
    assertEquals("not-a-date", cell.getStringCellValue());
  }

  @Test
  void testZeroIsNotExportedAsFirstOfJanuary1970() {
    // A "never connected" zero is not an instant
    Cell cell = cell();
    portlet.writeValue(cell, "0", true, formatting);

    assertEquals(CellType.NUMERIC, cell.getCellType());
    assertFalse(DateUtil.isCellDateFormatted(cell), "0 must not become 1 January 1970");
    assertEquals(0d, cell.getNumericCellValue());
  }

  @Test
  void testASpaceFieldColumnGoesThroughTheSameValueWriter() {
    // The space branch used to bypass writeValue entirely, so a createdTime
    // column - which carries dataType "date" - exported an epoch number
    Space space = new Space();
    space.setDisplayName("Marketing");
    space.setCreatedTime(1789055100000L);
    AnalyticsTableColumnFilter spaceColumn = new AnalyticsTableColumnFilter();
    spaceColumn.setSpaceField("createdTime");
    spaceColumn.setDataType("date");

    Cell cell = cell();
    portlet.writeCell(cell, spaceColumn, null, null, space, formatting);

    assertEquals(CellType.NUMERIC, cell.getCellType());
    assertTrue(DateUtil.isCellDateFormatted(cell), "A space createdTime column must reach the reader as a date");
    assertEquals(2026, cell.getLocalDateTimeCellValue().getYear());
  }

  @Test
  void testAUserFieldColumnResolvesCreatedDate() {
    // createdDate is not a Profile property: it lives in its own field, and
    // the key exists only on the REST DTO the live table reads
    Profile profile = new Profile();
    profile.setCreatedTime(1789055100000L);
    Identity identity = new Identity("organization", "john");
    identity.setProfile(profile);
    AnalyticsTableColumnFilter userColumn = new AnalyticsTableColumnFilter();
    userColumn.setUserField("createdDate");
    userColumn.setDataType("date");

    Cell cell = cell();
    portlet.writeCell(cell, userColumn, null, identity, null, formatting);

    assertEquals(CellType.NUMERIC, cell.getCellType());
    assertTrue(DateUtil.isCellDateFormatted(cell));
    assertEquals(2026, cell.getLocalDateTimeCellValue().getYear());
  }

  @Test
  void testAnAggregationColumnWithoutAValueIsEmpty() {
    Cell cell = cell();
    portlet.writeCell(cell, column("long", "activitiesCount"), null, null, null, formatting);

    assertEquals(CellType.STRING, cell.getCellType());
    assertEquals("", cell.getStringCellValue());
  }

  @Test
  void testAnAggregationColumnWritesItsValue() {
    Cell cell = cell();
    portlet.writeCell(cell,
                      column("long", "activitiesCount"),
                      new TableColumnItemValue("activitiesCount", "28", null, null, null),
                      null,
                      null,
                      formatting);

    assertEquals(CellType.NUMERIC, cell.getCellType());
    assertEquals(28d, cell.getNumericCellValue());
  }

  @Test
  void testOneStyleIsSharedByEveryDateCell() {
    // The style cache is per workbook: a new style per cell hits the 64k
    // cell-style ceiling on a large export
    Cell first = cell();
    Cell second = cell();
    portlet.writeValue(first, "1789055100000", true, formatting);
    portlet.writeValue(second, "1789141500000", true, formatting);

    assertEquals(first.getCellStyle().getIndex(), second.getCellStyle().getIndex());
    assertEquals(1, formatting.dateStyles().size());
  }

}
