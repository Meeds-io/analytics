/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io
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

import java.io.IOException;
import java.io.OutputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

import javax.portlet.PortletException;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;
import javax.ws.rs.core.MediaType;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONObject;

import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.space.model.Space;

import io.meeds.analytics.model.StatisticFieldMapping;
import io.meeds.analytics.model.chart.TableColumnItemValue;
import io.meeds.analytics.model.chart.TableColumnResult;
import io.meeds.analytics.model.filter.AnalyticsFilter;
import io.meeds.analytics.model.filter.AnalyticsPeriod;
import io.meeds.analytics.model.filter.AnalyticsPeriodType;
import io.meeds.analytics.model.filter.AnalyticsTableColumnAggregation;
import io.meeds.analytics.model.filter.AnalyticsTableColumnFilter;
import io.meeds.analytics.model.filter.AnalyticsTableFilter;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregation;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregationType;
import io.meeds.analytics.model.filter.search.AnalyticsFieldFilter;
import io.meeds.analytics.model.filter.search.AnalyticsFieldFilterType;
import io.meeds.analytics.utils.AnalyticsUtils;

public class AnalyticsTablePortlet extends AbstractAnalyticsPortlet<AnalyticsTableFilter> {

  /**
   * Hard cap on the number of rows a single export can contain. Elasticsearch
   * bucket aggregations always need a bounded size (there is no true
   * "unlimited" terms/date-histogram query), and this is comfortably under
   * the default {@code search.max_buckets} ceiling.
   */
  private static final int EXPORT_MAX_ROWS = 5000;

  @Override
  protected String getViewPagePath() {
    return "/WEB-INF/jsp/analytics-table.jsp";
  }

  @Override
  protected Class<AnalyticsTableFilter> getFilterClass() {
    return AnalyticsTableFilter.class;
  }

  @Override
  protected void readSettingsReadOnly(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
    AnalyticsTableFilter filter = getFilter(request);
    JSONObject jsonResponse = new JSONObject();
    addJSONParam(jsonResponse, "title", filter.getTitle());
    addJSONParam(jsonResponse, "pageSize", filter.getPageSize());
    addJSONParam(jsonResponse, "defaultPeriod", filter.getDefaultPeriod());
    addJSONParam(jsonResponse, "canEdit", canModifySettings(request));
    addJSONParam(jsonResponse, "scope", getSearchScope(request).name());
    response.setContentType("application/json");
    response.getWriter().write(jsonResponse.toString());
  }

  @Override
  protected void readSettings(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
    AnalyticsTableFilter filter = getFilter(request);
    response.setContentType("application/json");
    response.getWriter().write(AnalyticsUtils.toJsonString(filter));
  }

  @Override
  protected void readData(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
    AnalyticsTableFilter tableFilter = getFilter(request);
    if (tableFilter.getMainColumn() == null
        || tableFilter.getMainColumn().getValueAggregation() == null
        || tableFilter.getMainColumn().getValueAggregation().getAggregation() == null
        || tableFilter.getMainColumn().getValueAggregation().getAggregation().getField() == null) {
      response.setContentType(MediaType.APPLICATION_JSON);
      response.getWriter().write("{}");
      return;
    }
    addTimeZoneFilter(request, tableFilter);

    String column = request.getParameter("column");
    int columnIndex = 0;
    if (StringUtils.isNotBlank(column)) {
      columnIndex = Integer.parseInt(column);
    }
    String fromDateString = request.getParameter("min");
    String toDateString = request.getParameter("max");
    AnalyticsPeriod period = new AnalyticsPeriod(Long.parseLong(fromDateString),
                                                 Long.parseLong(toDateString),
                                                 tableFilter.zoneId());
    AnalyticsPeriodType periodType = null;
    String analyticsPeriodType = request.getParameter("periodType");
    if (StringUtils.isNotBlank(analyticsPeriodType)) {
      periodType = AnalyticsPeriodType.periodTypeByName(analyticsPeriodType);
      period = tableFilter.getCurrentPeriod(period, periodType);
    }
    AnalyticsFieldFilter fieldFilter = null;

    String fieldFilterName = request.getParameter("fieldFilter");
    String fieldFilterValues = request.getParameter("fieldValues");
    if (StringUtils.isNotBlank(fieldFilterName) && StringUtils.isNotBlank(fieldFilterValues)) {
      fieldFilter = new AnalyticsFieldFilter(fieldFilterName, AnalyticsFieldFilterType.IN_SET, fieldFilterValues);
    }

    int limit;
    try {
      limit = Integer.parseInt(request.getParameter("limit"));
    } catch (NumberFormatException e) {
      limit = 0;
    }
    String sort = request.getParameter("sort");

    AnalyticsFilter filter = tableFilter.buildColumnFilter(period,
                                                           periodType,
                                                           fieldFilter,
                                                           limit,
                                                           sort,
                                                           columnIndex,
                                                           true);
    addScopeFilter(request, filter);
    addLanguageFilter(request, filter);

    TableColumnResult result = getAnalyticsService().computeTableColumnData(null,
                                                                            tableFilter,
                                                                            filter,
                                                                            period,
                                                                            periodType,
                                                                            columnIndex,
                                                                            true);
    AnalyticsTableColumnFilter columnFilter = tableFilter.getColumnFilter(columnIndex);
    if (columnFilter.getThresholdAggregation() != null
        && columnFilter.getThresholdAggregation().getAggregation() != null
        && columnFilter.getThresholdAggregation().getAggregation().getType() != null
        && columnFilter.getThresholdAggregation().getAggregation().getField() != null) {
      filter = tableFilter.buildColumnFilter(period,
                                             periodType,
                                             fieldFilter,
                                             limit,
                                             sort,
                                             columnIndex,
                                             false);
      addScopeFilter(request, filter);
      addLanguageFilter(request, filter);

      getAnalyticsService().computeTableColumnData(result,
                                                   tableFilter,
                                                   filter,
                                                   period,
                                                   periodType,
                                                   columnIndex,
                                                   false);
    }

    response.setContentType(MediaType.APPLICATION_JSON);
    response.getWriter().write(AnalyticsUtils.toJsonString(result));
  }

  private AnalyticsTableFilter getFilter(ResourceRequest request) {
    AnalyticsTableFilter filter = getFilterFromPreferences(request);
    Set<StatisticFieldMapping> mappings = getAnalyticsService().retrieveMapping(false);
    List<AnalyticsTableColumnFilter> columns = filter.getColumns();
    for (AnalyticsTableColumnFilter analyticsTableColumnFilter : columns) {
      convertFieldName(analyticsTableColumnFilter, mappings);
    }
    convertFieldName(filter.getMainColumn(), mappings);
    return filter;
  }

  private void convertFieldName(AnalyticsTableColumnFilter columnFilter, Set<StatisticFieldMapping> mappings) {
    if (columnFilter != null) {
      AnalyticsUtils.convertFieldName(columnFilter::getUserField,
                                      columnFilter::setUserField,
                                      mappings,
                                      false);
      AnalyticsUtils.convertFieldName(columnFilter::getSpaceField,
                                      columnFilter::setSpaceField,
                                      mappings,
                                      false);
      convertFieldName(columnFilter.getThresholdAggregation(),
                       mappings);
      convertFieldName(columnFilter.getValueAggregation(),
                       mappings);
    }
  }

  private void convertFieldName(AnalyticsTableColumnAggregation columnAggregation, Set<StatisticFieldMapping> mappings) {
    if (columnAggregation != null) {
      AnalyticsAggregation aggregation = columnAggregation.getAggregation();
      if (aggregation != null) {
        AnalyticsUtils.convertFieldName(aggregation::getField,
                                        aggregation::setField,
                                        mappings,
                                        true);
      }
      List<AnalyticsFieldFilter> filters = columnAggregation.getFilters();
      if (CollectionUtils.isNotEmpty(filters)) {
        for (AnalyticsFieldFilter analyticsFilter : filters) {
          AnalyticsUtils.convertFieldName(analyticsFilter::getField,
                                          analyticsFilter::setField,
                                          mappings,
                                          false);
        }
      }
    }
  }

  /**
   * Fully server-side export: every row is recomputed here from the
   * configured filter (same aggregation calls as {@link #readData}, just
   * run once per column instead of once per page), so no row data ever
   * needs to be sent from the browser. Two known, deliberate simplifications
   * versus what is displayed on screen:
   * <ul>
   * <li>a column matched by a 3rd-party "cell value extension" (registered
   * by another addon via {@code extensionRegistry}) is exported with its
   * raw underlying value, since extension rendering only exists client-side
   * and has no server-side equivalent;</li>
   * <li>previous-period comparison columns are exported as-is (current
   * value only), the on-screen +/-% delta is not recomputed here.</li>
   * </ul>
   */
  @Override
  protected void exportExcel(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
    AnalyticsTableFilter tableFilter = getFilter(request);
    if (tableFilter.getMainColumn() == null
        || tableFilter.getMainColumn().getValueAggregation() == null
        || tableFilter.getMainColumn().getValueAggregation().getAggregation() == null
        || tableFilter.getMainColumn().getValueAggregation().getAggregation().getField() == null) {
      throw new PortletException("No column configured to export");
    }
    addTimeZoneFilter(request, tableFilter);

    String fromDateString = request.getParameter("min");
    String toDateString = request.getParameter("max");
    AnalyticsPeriod period = new AnalyticsPeriod(Long.parseLong(fromDateString), Long.parseLong(toDateString));
    AnalyticsPeriodType periodType = null;
    String analyticsPeriodType = request.getParameter("periodType");
    if (StringUtils.isNotBlank(analyticsPeriodType)) {
      periodType = AnalyticsPeriodType.periodTypeByName(analyticsPeriodType);
      period = tableFilter.getCurrentPeriod(period, periodType);
    }

    int sortByIndex = parseIntOrDefault(request.getParameter("sortBy"), parseIntOrDefault(tableFilter.getSortBy(), 0));
    String sortDirection = StringUtils.defaultIfBlank(request.getParameter("sortDirection"),
                                                      StringUtils.defaultIfBlank(tableFilter.getSortDirection(), "desc"));

    List<AnalyticsTableColumnFilter> columns = new ArrayList<>();
    columns.add(tableFilter.getMainColumn());
    if (tableFilter.getColumns() != null) {
      columns.addAll(tableFilter.getColumns());
    }
    String mainField = columns.get(0).getValueAggregation().getAggregation().getField();

    // The sorted/primary column is fetched first, with a bounded "give me
    // everything" limit: its keys and order become the rows of the sheet.
    TableColumnResult sortColumnResult = computeExportColumn(request,
                                                             tableFilter,
                                                             period,
                                                             periodType,
                                                             sortByIndex,
                                                             EXPORT_MAX_ROWS,
                                                             sortDirection,
                                                             null);
    List<String> rowKeys = sortColumnResult.getItems().stream().map(TableColumnItemValue::getKey).toList();

    // Every other aggregation-backed column is then fetched filtered down to
    // those exact same keys, so all columns describe the same rows (mirrors
    // AnalyticsTable.vue's refreshColumn "fieldFilter/fieldValues" call).
    Map<Integer, Map<String, TableColumnItemValue>> columnItemsByKey = new LinkedHashMap<>();
    columnItemsByKey.put(sortByIndex, indexByKey(sortColumnResult));
    for (int i = 0; i < columns.size(); i++) {
      AnalyticsTableColumnFilter columnFilter = columns.get(i);
      if (i == sortByIndex || columnFilter.getUserField() != null || columnFilter.getSpaceField() != null
          || columnFilter.getValueAggregation() == null || columnFilter.getValueAggregation().getAggregation() == null) {
        continue;
      }
      TableColumnResult columnResult = computeExportColumn(request,
                                                            tableFilter,
                                                            period,
                                                            periodType,
                                                            i,
                                                            EXPORT_MAX_ROWS,
                                                            null,
                                                            new SimpleEntry<>(mainField, rowKeys));
      columnItemsByKey.put(i, indexByKey(columnResult));
    }

    // Row identities (main column) are resolved once per row and shared by
    // every userField/spaceField sub-column, instead of one REST round-trip
    // per cell as the live table currently does.
    boolean mainColumnIsIdentity = isIdentityAggregation(mainField, columns.get(0).getValueAggregation().getAggregation().getType());
    Map<String, Identity> identityByKey = new HashMap<>();
    Map<String, Space> spaceByKey = new HashMap<>();
    if (mainColumnIsIdentity) {
      boolean space = StringUtils.equals(mainField, "spaceId");
      for (String key : rowKeys) {
        if (space) {
          spaceByKey.computeIfAbsent(key, AnalyticsUtils::getSpaceById);
        } else {
          identityByKey.computeIfAbsent(key, AnalyticsUtils::getIdentity);
        }
      }
    }

    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet(safeSheetName(resolveExportTitle(tableFilter.getTitle(), request.getLocale()), "Table"));

      Row headerRow = sheet.createRow(0);
      for (int col = 0; col < columns.size(); col++) {
        headerRow.createCell(col).setCellValue(resolveLabel(columns.get(col).getTitle(), request));
      }

      Set<String> dateFields = getAnalyticsService().retrieveMapping(false)
                                                    .stream()
                                                    .filter(StatisticFieldMapping::isDate)
                                                    .map(StatisticFieldMapping::getName)
                                                    .collect(Collectors.toSet());
      ExportFormatting formatting = new ExportFormatting(tableFilter.zoneId(),
                                                         request.getParameter("lang"),
                                                         new HashMap<>(),
                                                         dateFields);
      for (int rowIndex = 0; rowIndex < rowKeys.size(); rowIndex++) {
        String key = rowKeys.get(rowIndex);
        Row row = sheet.createRow(rowIndex + 1);
        for (int col = 0; col < columns.size(); col++) {
          writeCell(row.createCell(col),
                   columns.get(col),
                   columnItemsByKey.get(col) == null ? null : columnItemsByKey.get(col).get(key),
                   identityByKey.get(key),
                   spaceByKey.get(key),
                   formatting,
                   col == 0);
        }
      }
      for (int col = 0; col < columns.size(); col++) {
        sheet.autoSizeColumn(col);
      }

      response.setContentType(XLSX_CONTENT_TYPE);
      response.addProperty("Content-Disposition", "attachment; filename=" + buildFileName(tableFilter, request.getLocale()) + ".xlsx");
      try (OutputStream outputStream = response.getPortletOutputStream()) {
        workbook.write(outputStream);
      }
    }
  }

  private TableColumnResult computeExportColumn(ResourceRequest request,
                                                AnalyticsTableFilter tableFilter,
                                                AnalyticsPeriod period,
                                                AnalyticsPeriodType periodType,
                                                int columnIndex,
                                                int limit,
                                                String sort,
                                                Map.Entry<String, List<String>> fieldFilterEntry) throws PortletException {
    AnalyticsFieldFilter fieldFilter = null;
    if (fieldFilterEntry != null && CollectionUtils.isNotEmpty(fieldFilterEntry.getValue())) {
      fieldFilter = new AnalyticsFieldFilter(fieldFilterEntry.getKey(),
                                             AnalyticsFieldFilterType.IN_SET,
                                             String.join(",", fieldFilterEntry.getValue()));
    }
    AnalyticsFilter filter = tableFilter.buildColumnFilter(period, periodType, fieldFilter, limit, sort, columnIndex, true);
    addScopeFilter(request, filter);
    addLanguageFilter(request, filter);
    TableColumnResult result = getAnalyticsService().computeTableColumnData(null,
                                                                            tableFilter,
                                                                            filter,
                                                                            period,
                                                                            periodType,
                                                                            columnIndex,
                                                                            true);
    AnalyticsTableColumnFilter columnFilter = tableFilter.getColumnFilter(columnIndex);
    if (columnFilter.getThresholdAggregation() != null
        && columnFilter.getThresholdAggregation().getAggregation() != null
        && columnFilter.getThresholdAggregation().getAggregation().getType() != null
        && columnFilter.getThresholdAggregation().getAggregation().getField() != null) {
      filter = tableFilter.buildColumnFilter(period, periodType, fieldFilter, limit, sort, columnIndex, false);
      addScopeFilter(request, filter);
      addLanguageFilter(request, filter);
      getAnalyticsService().computeTableColumnData(result, tableFilter, filter, period, periodType, columnIndex, false);
    }
    return result;
  }

  private Map<String, TableColumnItemValue> indexByKey(TableColumnResult result) {
    Map<String, TableColumnItemValue> itemsByKey = new HashMap<>();
    if (result != null && result.getItems() != null) {
      result.getItems().forEach(item -> itemsByKey.put(item.getKey(), item));
    }
    return itemsByKey;
  }

  private boolean isIdentityAggregation(String field, AnalyticsAggregationType type) {
    return type == AnalyticsAggregationType.TERMS && (StringUtils.equals(field, "userId") || StringUtils.equals(field, "spaceId"));
  }

  /**
   * What every exported cell needs beyond its own value: the time zone the
   * buckets were aligned on, the language its labels are resolved in, and
   * the workbook-wide cache of date cell styles.
   */
  record ExportFormatting(ZoneId zoneId, String lang, Map<String, CellStyle> dateStyles, Set<String> dateFields) {

    boolean isDateField(String field) {
      return field != null && dateFields.contains(StringUtils.removeEnd(field, ".keyword"));
    }
  }

  /**
   * Aggregations whose result is a count, not an instant, whatever field they
   * are computed over. A CARDINALITY over a date field is a date column by
   * both signals below and its value is a number of distinct values: treated
   * as an instant, "28" would be exported as 28 ms after 1 January 1970.
   */
  private static final Set<AnalyticsAggregationType> COUNTING_AGGREGATIONS =
                                                                           Set.of(AnalyticsAggregationType.CARDINALITY,
                                                                                  AnalyticsAggregationType.COUNT,
                                                                                  AnalyticsAggregationType.TERMS,
                                                                                  AnalyticsAggregationType.GROUP_BY);

  /**
   * Whether the column holds dates, using the same signal the table itself
   * renders from: {@code dataType == "date"} makes AnalyticsTableCellValue
   * display a &lt;date-format&gt;. The Elasticsearch mapping is only a
   * fallback, for a column saved before the data type was recorded.
   *
   * @param mainColumn whether this is the table's first column, whose cell
   *                     holds the bucket <em>key</em> rather than an
   *                     aggregated value (ElasticsearchAnalyticsService:
   *                     {@code if (columnIndex == 0) itemValue.setValue(key)}).
   *                     Its aggregation is always TERMS - the settings form
   *                     forces it - so the counting exclusion must not apply
   *                     to it: over a date field that key is epoch
   *                     milliseconds, and excluding it exports the raw number
   *                     this delivery exists to remove.
   */
  boolean isDateColumn(AnalyticsTableColumnFilter columnFilter, ExportFormatting formatting, boolean mainColumn) {
    AnalyticsTableColumnAggregation valueAggregation = columnFilter.getValueAggregation();
    AnalyticsAggregation aggregation = valueAggregation == null ? null : valueAggregation.getAggregation();
    if (!mainColumn && aggregation != null && COUNTING_AGGREGATIONS.contains(aggregation.getType())) {
      return false;
    }
    if (StringUtils.equalsIgnoreCase(columnFilter.getDataType(), "date")) {
      return true;
    }
    return aggregation != null && formatting.isDateField(aggregation.getField());
  }

  void writeCell(Cell cell,
                         AnalyticsTableColumnFilter columnFilter,
                         TableColumnItemValue item,
                         Identity rowIdentity,
                         Space rowSpace,
                         ExportFormatting formatting,
                         boolean mainColumn) {
    boolean dateColumn = isDateColumn(columnFilter, formatting, mainColumn);
    if (StringUtils.isNotBlank(columnFilter.getUserField())) {
      writeValue(cell, userFieldValue(rowIdentity, columnFilter.getUserField()), dateColumn, formatting);
      return;
    } else if (StringUtils.isNotBlank(columnFilter.getSpaceField())) {
      // Through writeValue like every other branch: a createdTime column
      // carries dataType "date" and must reach the reader as a date, not as
      // an epoch number
      writeValue(cell, spaceFieldValue(rowSpace, columnFilter.getSpaceField()), dateColumn, formatting);
      return;
    }
    if (item == null || item.getValue() == null) {
      cell.setCellValue("");
      return;
    }
    AnalyticsAggregation aggregation = columnFilter.getValueAggregation().getAggregation();
    String rawValue = String.valueOf(item.getValue());
    if (StringUtils.isBlank(rawValue) || StringUtils.equals(rawValue, "null")) {
      // Same guard as writeValue, needed here too: the DATE and identity
      // branches below never reach it
      cell.setCellValue("");
    } else if (aggregation.getType() == AnalyticsAggregationType.DATE) {
      // A real date cell where the interval allows one, so the column sorts
      // and filters chronologically instead of alphabetically
      if (!writeDateCell(cell, aggregation, String.valueOf(item.getKey()), formatting.zoneId(), formatting.dateStyles())) {
        cell.setCellValue(aggregation.getLabel(String.valueOf(item.getKey()), formatting.zoneId(), formatting.lang()));
      }
    } else if (isIdentityAggregation(aggregation.getField(), aggregation.getType())) {
      cell.setCellValue(StringUtils.equals(aggregation.getField(), "spaceId") ? spaceFieldValue(rowSpace, "displayName")
                                                                              : (rowIdentity == null || rowIdentity.getProfile() == null ? rawValue
                                                                                                                                          : rowIdentity.getProfile()
                                                                                                                                                       .getFullName()));
    } else {
      writeValue(cell, rawValue, dateColumn, formatting);
    }
  }

  /**
   * Writes one value, as a date when the column holds dates. A MIN/MAX over
   * a date field is a numeric aggregation whose value is an instant in epoch
   * milliseconds: written as a plain number it reaches the reader as
   * 1.75941E+12.
   */
  void writeValue(Cell cell, String rawValue, boolean dateColumn, ExportFormatting formatting) {
    if (StringUtils.isBlank(rawValue) || StringUtils.equals(rawValue, "null")) {
      // No value for this row (a user who never connected, say). Exporting
      // the literal string "null" puts the word in the reader's spreadsheet.
      cell.setCellValue("");
      return;
    }
    if (dateColumn && writeTimestampCell(cell, rawValue, formatting.zoneId(), formatting.dateStyles())) {
      return;
    }
    try {
      cell.setCellValue(Double.parseDouble(rawValue));
    } catch (NumberFormatException e) {
      cell.setCellValue(rawValue);
    }
  }

  /**
   * The value of a user-profile column.
   * <p>
   * {@code createdDate} - the only user field the settings UI offers - is not
   * a {@link org.exoplatform.social.core.identity.model.Profile} property:
   * {@code getProperty} is a plain map lookup, and the creation instant lives
   * in its own {@code createdTime} field. The key exists only on the REST
   * DTO, which is what the live table reads client-side; without this case
   * the column exported an empty cell while the screen showed a date.
   */
  private String userFieldValue(Identity rowIdentity, String field) {
    Profile profile = rowIdentity == null ? null : rowIdentity.getProfile();
    if (profile == null) {
      return "";
    }
    if (StringUtils.equals(field, "createdDate")) {
      return String.valueOf(profile.getCreatedTime());
    }
    Object property = profile.getProperty(field);
    return property == null ? "" : String.valueOf(property);
  }

  /**
   * One case per space field the settings UI offers
   * (AnalyticsTableApplication.vue, spaceFields), plus {@code displayName}
   * for a space identity main column, plus the legacy cases -
   * {@code description}, {@code groupId}, {@code prettyName},
   * {@code shortName}, {@code url} - which the UI does not offer and which
   * exist for settings saved before that list and for the JSON settings
   * drawer.
   * <p>
   * The default is deliberately empty rather than the display name: with a
   * display-name fallback, every field missing a case exported the space name
   * and looked like data, so the seven fields the UI actually offers -
   * none of which had a case - silently exported the wrong column.
   */
  private String spaceFieldValue(Space space, String field) {
    if (space == null) {
      return "";
    }
    return switch (field) {
    case "displayName" -> space.getDisplayName();
    case "description" -> space.getDescription();
    case "groupId" -> space.getGroupId();
    case "prettyName" -> space.getPrettyName();
    case "shortName" -> space.getShortName();
    case "url" -> space.getUrl();
    case "createdTime" -> String.valueOf(space.getCreatedTime());
    case "visibility" -> space.getVisibility();
    case "subscription" -> space.getRegistration();
    case "template" -> String.valueOf(space.getTemplateId());
    case "managersCount" -> String.valueOf(ArrayUtils.getLength(space.getManagers()));
    case "membersCount" -> String.valueOf(ArrayUtils.getLength(space.getMembers()));
    case "redactorsCount" -> String.valueOf(ArrayUtils.getLength(space.getRedactors()));
    default -> "";
    };
  }

  private String resolveLabel(String key, ResourceRequest request) {
    if (StringUtils.isBlank(key)) {
      return "";
    }
    try {
      ResourceBundle bundle = getPortletConfig().getResourceBundle(request.getLocale());
      if (bundle != null && bundle.containsKey(key)) {
        return bundle.getString(key);
      }
    } catch (Exception e) {
      // Not a resource-bundle key: use as literal text (a user-typed column
      // title), matching how the frontend's $t() call already behaves.
    }
    return key;
  }

  private int parseIntOrDefault(String value, int defaultValue) {
    try {
      return StringUtils.isBlank(value) ? defaultValue : Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private String buildFileName(AnalyticsTableFilter filter, Locale locale) {
    String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(ZonedDateTime.now(filter.zoneId()));
    return sanitizeFileName(resolveExportTitle(filter.getTitle(), locale)) + "_" + timestamp;
  }

  private String sanitizeFileName(String title) {
    String sanitized = StringUtils.isBlank(title) ? "" : title.replaceAll("[^a-zA-Z0-9-_]", "_");
    return StringUtils.isBlank(sanitized) ? "analytics-table" : sanitized;
  }

}
