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

import static io.meeds.analytics.utils.AnalyticsUtils.convertFieldName;

import java.io.IOException;
import java.io.OutputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.portlet.PortletException;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;
import javax.ws.rs.core.MediaType;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;

import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.StatisticFieldMapping;
import io.meeds.analytics.model.chart.ChartAggregationResult;
import io.meeds.analytics.model.chart.ChartData;
import io.meeds.analytics.model.chart.ChartDataList;
import io.meeds.analytics.model.filter.AnalyticsFilter;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregation;
import io.meeds.analytics.model.filter.search.AnalyticsFieldFilter;
import io.meeds.analytics.utils.AnalyticsUtils;

public class AnalyticsPortlet extends AbstractAnalyticsPortlet<AnalyticsFilter> {

  @Override
  protected String getViewPagePath() {
    return "/WEB-INF/jsp/analytics.jsp";
  }

  @Override
  protected Class<AnalyticsFilter> getFilterClass() {
    return AnalyticsFilter.class;
  }

  @Override
  protected void readSettings(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
    AnalyticsFilter filter = getFilter(request);
    response.setContentType(MediaType.APPLICATION_JSON);
    response.getWriter().write(AnalyticsUtils.toJsonString(filter));
  }

  @Override
  protected void readSettingsReadOnly(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
    AnalyticsFilter filter = getFilter(request);
    JSONObject jsonResponse = new JSONObject();
    addJSONParam(jsonResponse, "title", filter.getTitle());
    addJSONParam(jsonResponse, "chartType", filter.getChartType());
    addJSONParam(jsonResponse, "defaultPeriod", filter.getDefaultPeriod());
    List<String> colors = filter.getColors() == null ? Collections.emptyList() : filter.getColors();
    addJSONParam(jsonResponse, "colors", new JSONArray(colors));
    addJSONParam(jsonResponse, "canEdit", canModifySettings(request));
    addJSONParam(jsonResponse, "scope", getSearchScope(request).name());
    response.setContentType(MediaType.APPLICATION_JSON);
    response.getWriter().write(jsonResponse.toString());
  }

  @Override
  protected void readSamples(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
    AnalyticsFilter filter = getFilter(request);
    addPeriodFilter(request, filter);
    addScopeFilter(request, filter);
    addLanguageFilter(request, filter);
    addLimitFilter(request, filter);
    addTimeZoneFilter(request, filter);
    addSortFilter(filter, "desc");

    List<StatisticData> statisticDatas = getAnalyticsService().retrieveData(filter);
    List<JSONObject> objectMappings = statisticDatas.stream()
                                                    .map(statisticData -> {
                                                      JSONObject object = new JSONObject(statisticData);
                                                      object.remove("class");
                                                      return object;
                                                    })
                                                    .toList();
    JSONArray jsonArrayResponse = new JSONArray(objectMappings);
    response.setContentType(MediaType.APPLICATION_JSON);
    response.getWriter().write(jsonArrayResponse.toString());
  }

  @Override
  protected void readData(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
    AnalyticsFilter filter = getFilter(request);
    addPeriodFilter(request, filter);
    addScopeFilter(request, filter);
    addLanguageFilter(request, filter);
    addTimeZoneFilter(request, filter);

    Object result = getAnalyticsService().computeChartData(filter);
    response.setContentType(MediaType.APPLICATION_JSON);
    response.getWriter().write(AnalyticsUtils.toJsonString(result));
  }

  @Override
  protected void exportExcel(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
    AnalyticsFilter filter = getFilter(request);
    addPeriodFilter(request, filter);
    addScopeFilter(request, filter);
    addLanguageFilter(request, filter);
    addTimeZoneFilter(request, filter);

    ChartDataList chartDataList = getAnalyticsService().computeChartData(filter);
    boolean english = request.getLocale() != null && StringUtils.startsWithIgnoreCase(request.getLocale().getLanguage(), "en");
    boolean pie = StringUtils.equals(filter.getChartType(), "pie");

    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet(english ? "Chart" : "Graphique");
      int columnsCount = pie ? writePieSheet(sheet, chartDataList, english)
                             : writeSeriesSheet(sheet, chartDataList, english, getXAxisFieldName(filter));
      for (int i = 0; i < columnsCount; i++) {
        sheet.autoSizeColumn(i);
      }

      response.setContentType("application/vnd.ms-excel");
      response.addProperty("Content-Disposition", "attachment; filename=" + buildFileName(filter) + ".xlsx");
      try (OutputStream outputStream = response.getPortletOutputStream()) {
        workbook.write(outputStream);
      }
    }
  }

  /**
   * Writes one row per x-axis category (as displayed on the chart), one
   * column per series, mirroring the data actually shown on a line/bar/area
   * chart rather than the raw collected samples.
   *
   * @return the number of columns written, for later auto-sizing
   */
  private int writeSeriesSheet(Sheet sheet, ChartDataList chartDataList, boolean english, String xAxisFieldName) {
    List<String> labels = chartDataList.getLabels();
    List<ChartData> charts = new ArrayList<>(chartDataList.getCharts());

    Row headerRow = sheet.createRow(0);
    headerRow.createCell(0).setCellValue(StringUtils.isBlank(xAxisFieldName) ? (english ? "Category" : "Catégorie") : xAxisFieldName);
    for (int col = 0; col < charts.size(); col++) {
      headerRow.createCell(col + 1).setCellValue(seriesLabel(charts.get(col).getChartLabel(), english));
    }

    for (int rowIndex = 0; rowIndex < labels.size(); rowIndex++) {
      Row row = sheet.createRow(rowIndex + 1);
      row.createCell(0).setCellValue(labels.get(rowIndex));
      for (int col = 0; col < charts.size(); col++) {
        List<String> values = charts.get(col).getValues();
        row.createCell(col + 1).setCellValue(rowIndex < values.size() ? parseDouble(values.get(rowIndex)) : 0d);
      }
    }
    return charts.size() + 1;
  }

  /**
   * Writes one row per pie segment (as displayed on the chart). When the
   * chart is split into several pies (multiple charts field), an extra
   * leading column identifies which pie a segment belongs to.
   *
   * @return the number of columns written, for later auto-sizing
   */
  private int writePieSheet(Sheet sheet, ChartDataList chartDataList, boolean english) {
    List<ChartData> charts = new ArrayList<>(chartDataList.getCharts());
    boolean multipleCharts = charts.size() > 1;

    Row headerRow = sheet.createRow(0);
    int col = 0;
    if (multipleCharts) {
      headerRow.createCell(col++).setCellValue(english ? "Chart" : "Graphique");
    }
    headerRow.createCell(col++).setCellValue(english ? "Segment" : "Segment");
    headerRow.createCell(col).setCellValue(english ? "Value" : "Valeur");

    int rowIndex = 1;
    for (ChartData chartData : charts) {
      for (ChartAggregationResult result : chartData.getAggregationResults()) {
        Row row = sheet.createRow(rowIndex++);
        col = 0;
        if (multipleCharts) {
          row.createCell(col++).setCellValue(seriesLabel(chartData.getChartLabel(), english));
        }
        row.createCell(col++).setCellValue(result.getLabel());
        row.createCell(col).setCellValue(parseDouble(result.getValue()));
      }
    }
    return multipleCharts ? 3 : 2;
  }

  private double parseDouble(String value) {
    try {
      return StringUtils.isBlank(value) ? 0d : Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return 0d;
    }
  }

  /**
   * A series label of literal string {@code "null"} means the chart has no
   * real series name (single, unsplit series) — {@link AnalyticsUtils#compueLabel}
   * concatenates a null key and null value into that literal string rather
   * than returning an actual null.
   */
  private String seriesLabel(String chartLabel, boolean english) {
    return StringUtils.isBlank(chartLabel) || StringUtils.equals(chartLabel, "null") ? (english ? "Data" : "Données") : chartLabel;
  }

  private String getXAxisFieldName(AnalyticsFilter filter) {
    return filter.getXAxisAggregations()
                 .stream()
                 .findFirst()
                 .map(AnalyticsAggregation::getField)
                 .map(field -> field.replace(".keyword", ""))
                 .orElse(null);
  }

  private String buildFileName(AnalyticsFilter filter) {
    String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(ZonedDateTime.now(filter.zoneId()));
    return sanitizeFileName(filter.getTitle()) + "_" + timestamp;
  }

  private String sanitizeFileName(String title) {
    String sanitized = StringUtils.isBlank(title) ? "" : title.replaceAll("[^a-zA-Z0-9-_]", "_");
    return StringUtils.isBlank(sanitized) ? "analytics-chart" : sanitized;
  }

  private AnalyticsFilter getFilter(ResourceRequest request) {
    AnalyticsFilter filter = getFilterFromPreferences(request);
    Set<StatisticFieldMapping> mappings = getAnalyticsService().retrieveMapping(false);

    if (StringUtils.isNotBlank(filter.getMultipleChartsField())) {
      convertFieldName(filter::getMultipleChartsField,
                       filter::setMultipleChartsField,
                       mappings,
                       true);
    }
    if (CollectionUtils.isNotEmpty(filter.getAggregations())) {
      for (AnalyticsAggregation analyticsAggregation : filter.getAggregations()) {
        convertFieldName(analyticsAggregation::getField,
                         analyticsAggregation::setField,
                         mappings,
                         true);
      }
    }
    if (CollectionUtils.isNotEmpty(filter.getFilters())) {
      for (AnalyticsFieldFilter analyticsFilter : filter.getFilters()) {
        convertFieldName(analyticsFilter::getField,
                         analyticsFilter::setField,
                         mappings,
                         false);
      }
    }
    return filter;
  }

}
