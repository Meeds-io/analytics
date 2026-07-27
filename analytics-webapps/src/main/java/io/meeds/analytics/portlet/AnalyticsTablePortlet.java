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
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import javax.portlet.PortletException;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;
import javax.ws.rs.core.MediaType;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONObject;

import org.exoplatform.social.core.identity.model.Identity;
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
    AnalyticsPeriod period = new AnalyticsPeriod(Long.parseLong(fromDateString), Long.parseLong(toDateString));
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
                                                            0,
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
      Sheet sheet = workbook.createSheet(StringUtils.isBlank(tableFilter.getTitle()) ? "Table" : tableFilter.getTitle());

      Row headerRow = sheet.createRow(0);
      for (int col = 0; col < columns.size(); col++) {
        headerRow.createCell(col).setCellValue(resolveLabel(columns.get(col).getTitle(), request));
      }

      ZoneId zoneId = tableFilter.zoneId();
      String lang = request.getParameter("lang");
      for (int rowIndex = 0; rowIndex < rowKeys.size(); rowIndex++) {
        String key = rowKeys.get(rowIndex);
        Row row = sheet.createRow(rowIndex + 1);
        for (int col = 0; col < columns.size(); col++) {
          writeCell(row.createCell(col),
                   columns.get(col),
                   columnItemsByKey.get(col) == null ? null : columnItemsByKey.get(col).get(key),
                   identityByKey.get(key),
                   spaceByKey.get(key),
                   zoneId,
                   lang);
        }
      }
      for (int col = 0; col < columns.size(); col++) {
        sheet.autoSizeColumn(col);
      }

      response.setContentType("application/vnd.ms-excel");
      response.addProperty("Content-Disposition", "attachment; filename=" + buildFileName(tableFilter) + ".xlsx");
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

  private void writeCell(Cell cell,
                         AnalyticsTableColumnFilter columnFilter,
                         TableColumnItemValue item,
                         Identity rowIdentity,
                         Space rowSpace,
                         ZoneId zoneId,
                         String lang) {
    if (StringUtils.isNotBlank(columnFilter.getUserField())) {
      cell.setCellValue(rowIdentity == null || rowIdentity.getProfile() == null ? "" :
                        String.valueOf(rowIdentity.getProfile().getProperty(columnFilter.getUserField())));
      return;
    } else if (StringUtils.isNotBlank(columnFilter.getSpaceField())) {
      cell.setCellValue(spaceFieldValue(rowSpace, columnFilter.getSpaceField()));
      return;
    }
    if (item == null || item.getValue() == null) {
      cell.setCellValue("");
      return;
    }
    AnalyticsAggregation aggregation = columnFilter.getValueAggregation().getAggregation();
    String rawValue = String.valueOf(item.getValue());
    if (aggregation.getType() == AnalyticsAggregationType.DATE) {
      cell.setCellValue(aggregation.getLabel(String.valueOf(item.getKey()), zoneId, lang));
    } else if (isIdentityAggregation(aggregation.getField(), aggregation.getType())) {
      cell.setCellValue(StringUtils.equals(aggregation.getField(), "spaceId") ? spaceFieldValue(rowSpace, "displayName")
                                                                              : (rowIdentity == null || rowIdentity.getProfile() == null ? rawValue
                                                                                                                                          : rowIdentity.getProfile()
                                                                                                                                                       .getFullName()));
    } else {
      try {
        cell.setCellValue(Double.parseDouble(rawValue));
      } catch (NumberFormatException e) {
        cell.setCellValue(rawValue);
      }
    }
  }

  private String spaceFieldValue(Space space, String field) {
    if (space == null) {
      return "";
    }
    return switch (field) {
    case "description" -> space.getDescription();
    case "groupId" -> space.getGroupId();
    case "prettyName" -> space.getPrettyName();
    case "shortName" -> space.getShortName();
    case "url" -> space.getUrl();
    default -> space.getDisplayName();
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

  private String buildFileName(AnalyticsTableFilter filter) {
    String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(ZonedDateTime.now(filter.zoneId()));
    return sanitizeFileName(filter.getTitle()) + "_" + timestamp;
  }

  private String sanitizeFileName(String title) {
    String sanitized = StringUtils.isBlank(title) ? "" : title.replaceAll("[^a-zA-Z0-9-_]", "_");
    return StringUtils.isBlank(sanitized) ? "analytics-table" : sanitized;
  }

}
