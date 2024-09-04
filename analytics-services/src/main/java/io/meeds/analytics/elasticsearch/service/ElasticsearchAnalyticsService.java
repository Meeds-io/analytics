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
package io.meeds.analytics.elasticsearch.service;

import static io.meeds.analytics.utils.AnalyticsUtils.DEFAULT_FIELDS;
import static io.meeds.analytics.utils.AnalyticsUtils.FIELD_TIMESTAMP;
import static io.meeds.analytics.utils.AnalyticsUtils.collectionToJSONString;
import static io.meeds.analytics.utils.AnalyticsUtils.fixJSONStringFormat;
import static io.meeds.analytics.utils.AnalyticsUtils.fromJsonString;
import static io.meeds.analytics.utils.AnalyticsUtils.getJSONObject;
import static io.meeds.analytics.utils.AnalyticsUtils.toJsonString;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.analytics.api.service.AnalyticsService;
import io.meeds.analytics.elasticsearch.storage.ElasticsearchAnalyticsStorage;
import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.StatisticData.StatisticStatus;
import io.meeds.analytics.model.StatisticFieldMapping;
import io.meeds.analytics.model.StatisticFieldValue;
import io.meeds.analytics.model.StatisticWatcher;
import io.meeds.analytics.model.chart.ChartAggregationLabel;
import io.meeds.analytics.model.chart.ChartAggregationResult;
import io.meeds.analytics.model.chart.ChartAggregationValue;
import io.meeds.analytics.model.chart.ChartData;
import io.meeds.analytics.model.chart.ChartDataList;
import io.meeds.analytics.model.chart.PercentageChartResult;
import io.meeds.analytics.model.chart.PercentageChartValue;
import io.meeds.analytics.model.chart.TableColumnItemValue;
import io.meeds.analytics.model.chart.TableColumnResult;
import io.meeds.analytics.model.filter.AnalyticsFilter;
import io.meeds.analytics.model.filter.AnalyticsFilter.Range;
import io.meeds.analytics.model.filter.AnalyticsPercentageFilter;
import io.meeds.analytics.model.filter.AnalyticsPeriod;
import io.meeds.analytics.model.filter.AnalyticsPeriodType;
import io.meeds.analytics.model.filter.AnalyticsTableColumnFilter;
import io.meeds.analytics.model.filter.AnalyticsTableFilter;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregation;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregationType;
import io.meeds.analytics.model.filter.aggregation.AnalyticsPercentageLimit;
import io.meeds.analytics.model.filter.search.AnalyticsFieldFilter;
import io.meeds.common.ContainerTransactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;

@Primary
@Service
public class ElasticsearchAnalyticsService implements AnalyticsService {

  private static final String                VALUE_PARAM                              = "value";

  private static final String                SORT_DIRECTION_REQUEST_BODY_PARAM        = "$sortDirection";

  private static final String                SORT_FIELD_REQUEST_BODY_PARAM            = "$sortField";

  private static final String                SIZE_REQUEST_BODY_PARAM                  = "$size";

  private static final String                MAX_BOUND_REQUEST_BODY_PARAM             = "$maxBound";

  private static final String                MIN_BOUND_REQUEST_BODY_PARAM             = "$minBound";

  private static final String                MAX_REQUEST_BODY_PARAM                   = "$max";

  private static final String                MIN_REQUEST_BODY_PARAM                   = "$min";

  private static final String                TIME_ZONE_ID_REQUEST_BODY_PARAM          = "$timeZoneId";

  private static final String                INTERVAL_OFFSET_REQUEST_BODY_PARAM       = "$intervalOffset";

  private static final String                INTERVAL_REQUEST_BODY_PARAM              = "$interval";

  private static final String                AGG_FIELD_NAME_REQUEST_BODY_PARAM        = "$aggFieldName";

  private static final String                AGG_NAME_REQUEST_BODY_PARAM              = "$aggName";

  private static final String                VALUE_REQUEST_BODY_PARAM                 = "$value";

  private static final String                NAME_REQUEST_BODY_PARAM                  = "$name";

  private static final String                BUCKETS_RESPONSE_BODY                    = "buckets";

  private static final String                AGGREGATIONS_RESPONSE_BODY               = "aggregations";

  private static final String                ERROR_PARSING_RESULTS_MESSAGE            =
                                                                           "Error parsing results with - filter: %s - query: %s  - response: %s";

  private static final String                FILTER_AGGREGATIONS_IS_MANDATORY_MESSAGE = "Filter aggregations is mandatory";

  private static final String                FILTER_IS_MANDATORY_MESSAGE              = "Filter is mandatory";

  private static final Log                   LOG                                      =
                                                 ExoLogger.getLogger(ElasticsearchAnalyticsService.class);

  private static final String                AGGREGATION_KEYS_SEPARATOR               = "-";

  private static final String                AGGREGATION_RESULT_PARAM                 = "aggregation_result";

  private static final String                AGGREGATION_RESULT_VALUE_PARAM           = "aggregation_result_value";

  private static final String                AGGREGATION_BUCKETS_VALUE_PARAM          = "aggregation_buckets_value";

  private static final Context               CONTEXT                                  = Context.GLOBAL.id("ANALYTICS");

  private static final Scope                 ES_SCOPE                                 = Scope.GLOBAL.id("elasticsearch");

  private static final String                ES_AGGREGATED_MAPPING                    = "ES_AGGREGATED_MAPPING";

  @Autowired
  private ElasticsearchAnalyticsStorage      elasticsearchStorage;

  @Autowired
  private SettingService                     settingService;

  private List<StatisticWatcher>             uiWatchers                               = new ArrayList<>();

  private Map<String, StatisticFieldMapping> esMappings                               = new HashMap<>();

  private ScheduledExecutorService           esMappingUpdater                         = Executors.newScheduledThreadPool(1);

  @Value("${analytics.aggregation.terms.doc_size:200}")
  private int                                aggregationReturnedDocumentsSize;

  @Getter
  @Value("${analytics.admin.permission:*:/platform/analytics}")
  List<String>                               administratorsPermissions;

  @Getter
  @Value("${analytics.viewall.permissions:*:/platform/administrators}")
  List<String>                               viewAllPermissions;

  @Getter
  @Value("${analytics.view.permission:*:/platform/users}")
  List<String>                               viewPermissions;

  @PostConstruct
  public void start() {
    // Can't be job, because the mapping retrival must be executed on each
    // cluster node
    esMappingUpdater.scheduleAtFixedRate(() -> retrieveMapping(true),
                                         1,
                                         2,
                                         TimeUnit.MINUTES);
  }

  @PreDestroy
  public void stop() {
    esMappingUpdater.shutdown();
  }

  @Override
  @ContainerTransactional
  public Set<StatisticFieldMapping> retrieveMapping(boolean forceRefresh) {
    if (!forceRefresh) {
      if (esMappings.isEmpty()) {
        readFieldsMapping();
      }
      return new HashSet<>(esMappings.values());
    }
    try {
      String mappingJsonString = elasticsearchStorage.retrieveAllAnalyticsIndexesMapping();
      if (StringUtils.isBlank(mappingJsonString)) {
        return new HashSet<>(esMappings.values());
      }
      JSONObject result = new JSONObject(mappingJsonString);
      JSONObject mappingObject = getJSONObject(result,
                                               0,
                                               null,
                                               "mappings",
                                               "properties");
      if (mappingObject != null) {
        String[] fieldNames = JSONObject.getNames(mappingObject);
        for (String fieldName : fieldNames) {
          JSONObject esField = mappingObject.getJSONObject(fieldName);
          if (esField == null || !esField.has("type")) {
            continue;
          }
          String fieldType = esField.getString("type");
          JSONObject keywordField = getJSONObject(esField, 0, "fields", "keyword");
          StatisticFieldMapping esFieldMapping = new StatisticFieldMapping(fieldName, fieldType, keywordField != null);
          esMappings.put(fieldName, esFieldMapping);
        }
      }

      // Add other timestamp fields
      addESDateSubField("hourOfDay");
      addESDateSubField("dayOfMonth");
      addESDateSubField("dayOfWeek");
      addESDateSubField("dayOfYear");
      addESDateSubField("monthOfYear");
      addESDateSubField("year");

      storeFieldsMappings();
    } catch (Exception e) {
      LOG.error("Error getting mapping of analytics", e);
    }
    return new HashSet<>(esMappings.values());
  }

  @Override
  public List<StatisticFieldValue> retrieveFieldValues(String field, int limit) {
    StatisticFieldMapping fieldMapping = getFieldMapping(field);
    if (fieldMapping == null || !fieldMapping.isAggregation()) {
      return Collections.emptyList();
    }
    String esQuery = buildAnalyticsQuery(Collections.singletonList(new AnalyticsAggregation(AnalyticsAggregationType.TERMS,
                                                                                            fieldMapping.getAggregationFieldName(),
                                                                                            "desc",
                                                                                            null,
                                                                                            limit)),
                                         null,
                                         null,
                                         0,
                                         0);
    String jsonResponse = this.elasticsearchStorage.search(esQuery);
    try {
      return buildFieldValuesResponse(jsonResponse);
    } catch (JSONException e) {
      throw new IllegalStateException("Error parsing results for field: " + field + ", response: " + jsonResponse, e);
    }
  }

  @Override
  public PercentageChartResult computePercentageChartData(AnalyticsPercentageFilter percentageFilter) {
    if (percentageFilter == null) {
      throw new IllegalArgumentException(FILTER_IS_MANDATORY_MESSAGE);
    }
    AnalyticsPeriod currentPeriod = percentageFilter.getCurrentAnalyticsPeriod();
    AnalyticsPeriod previousPeriod = percentageFilter.getPreviousAnalyticsPeriod();

    PercentageChartResult percentageChartResult = new PercentageChartResult();
    AnalyticsPercentageLimit percentageLimit = percentageFilter.getPercentageLimit();

    if (percentageLimit != null) {

      computePercentageLimits(percentageFilter, percentageChartResult, currentPeriod, previousPeriod);

      long currentPeriodLimit = percentageFilter.getCurrentPeriodLimit();

      if (currentPeriodLimit > 0) {
        computePercentageValuesPerPeriod(percentageChartResult,
                                         percentageFilter.computeValueFilter(currentPeriod, currentPeriodLimit),
                                         currentPeriod,
                                         null,
                                         true,
                                         true);
      }

      long previousPeriodLimit = percentageFilter.getPreviousPeriodLimit();
      if (previousPeriodLimit > 0) {
        computePercentageValuesPerPeriod(percentageChartResult,
                                         percentageFilter.computeValueFilter(previousPeriod, previousPeriodLimit),
                                         null,
                                         previousPeriod,
                                         true,
                                         true);
      }
    } else {
      computePercentageValuesPerPeriod(percentageChartResult,
                                       percentageFilter.computeValueFilter(),
                                       currentPeriod,
                                       previousPeriod,
                                       true,
                                       false);
    }

    computePercentageValuesPerPeriod(percentageChartResult,
                                     percentageFilter.computeThresholdFilter(),
                                     currentPeriod,
                                     previousPeriod,
                                     false,
                                     false);
    return percentageChartResult;
  }

  @Override
  public TableColumnResult computeTableColumnData(TableColumnResult columnResult,
                                                  AnalyticsTableFilter tableFilter,
                                                  AnalyticsFilter filter,
                                                  AnalyticsPeriod period,
                                                  AnalyticsPeriodType periodType,
                                                  int columnIndex,
                                                  boolean isValue) {
    if (filter == null) {
      throw new IllegalArgumentException(FILTER_IS_MANDATORY_MESSAGE);
    }
    if (filter.getAggregations() == null || filter.getAggregations().isEmpty()) {
      throw new IllegalArgumentException(FILTER_AGGREGATIONS_IS_MANDATORY_MESSAGE);
    }

    String esQueryString = buildAnalyticsQuery(filter.getAggregations(),
                                               filter.getFilters(),
                                               filter.zoneId(),
                                               filter.getOffset(),
                                               filter.getLimit());

    String jsonResponse = this.elasticsearchStorage.search(esQueryString);
    try {
      return buildTableColumnDataFromESResponse(columnResult,
                                                tableFilter,
                                                period,
                                                periodType,
                                                columnIndex,
                                                isValue,
                                                jsonResponse,
                                                filter.getLimit());
    } catch (JSONException e) {
      throw new IllegalStateException(String.format(ERROR_PARSING_RESULTS_MESSAGE,
                                                    filter,
                                                    esQueryString,
                                                    jsonResponse),
                                      e);
    }
  }

  @Override
  public ChartDataList computeChartData(AnalyticsFilter filter) {
    if (filter == null) {
      throw new IllegalArgumentException(FILTER_IS_MANDATORY_MESSAGE);
    }
    if (filter.getAggregations() == null || filter.getAggregations().isEmpty()) {
      throw new IllegalArgumentException(FILTER_AGGREGATIONS_IS_MANDATORY_MESSAGE);
    }

    String esQueryString = buildAnalyticsQuery(filter.getAggregations(),
                                               filter.getFilters(),
                                               filter.zoneId(),
                                               filter.getOffset(),
                                               filter.getLimit());

    String jsonResponse = this.elasticsearchStorage.search(esQueryString);
    try {
      return buildChartDataFromESResponse(filter, jsonResponse);
    } catch (JSONException e) {
      throw new IllegalStateException(String.format(ERROR_PARSING_RESULTS_MESSAGE,
                                                    filter,
                                                    esQueryString,
                                                    jsonResponse),
                                      e);
    }
  }

  @Override
  public PercentageChartValue computePercentageChartData(AnalyticsFilter filter,
                                                         AnalyticsPeriod currentPeriod,
                                                         AnalyticsPeriod previousPeriod,
                                                         boolean hasLimitAggregation) {
    if (filter == null) {
      throw new IllegalArgumentException(FILTER_IS_MANDATORY_MESSAGE);
    }
    if (filter.getAggregations() == null || filter.getAggregations().isEmpty()) {
      throw new IllegalArgumentException(FILTER_AGGREGATIONS_IS_MANDATORY_MESSAGE);
    }
    AnalyticsAggregation yAxisAggregation = filter.getYAxisAggregation();
    AnalyticsAggregationType aggregationType = yAxisAggregation == null ? AnalyticsAggregationType.COUNT :
                                                                        yAxisAggregation.getType();
    String esQueryString = buildAnalyticsQuery(filter.getAggregations(),
                                               filter.getFilters(),
                                               aggregationType,
                                               hasLimitAggregation,
                                               filter.zoneId(),
                                               filter.getOffset(),
                                               filter.getLimit());

    String jsonResponse = this.elasticsearchStorage.search(esQueryString);
    try {
      return buildPercentageChartValuesFromESResponse(jsonResponse, currentPeriod, previousPeriod);
    } catch (JSONException e) {
      throw new IllegalStateException(String.format(ERROR_PARSING_RESULTS_MESSAGE,
                                                    filter,
                                                    esQueryString,
                                                    jsonResponse),
                                      e);
    }
  }

  @Override
  public List<StatisticData> retrieveData(AnalyticsFilter filter) {
    List<AnalyticsFieldFilter> filters = filter == null ? Collections.emptyList() : filter.getFilters();
    long offset = filter == null ? 0 : filter.getOffset();
    long limit = filter == null ? 10 : filter.getLimit();
    ZoneId timeZone = filter == null ? null : filter.zoneId();
    String esQueryString = buildAnalyticsQuery(null, filters, timeZone, offset, limit);
    String jsonResponse = this.elasticsearchStorage.search(esQueryString);
    try {
      return buildSearchResultFromESResponse(jsonResponse);
    } catch (JSONException e) {
      throw new IllegalStateException("Error parsing results with filter: " + filter + ", response: " + jsonResponse, e);
    }
  }

  @Override
  public List<StatisticWatcher> getUIWatchers() {
    return uiWatchers;
  }

  @Override
  public StatisticWatcher getUIWatcher(String name) {
    return uiWatchers.stream()
                     .filter(watcher -> StringUtils.equals(name, watcher.getName()))
                     .findFirst()
                     .orElse(null);
  }

  @Override
  public void addUIWatcher(StatisticWatcher uiWatcher) {
    uiWatchers.add(uiWatcher);
  }

  private List<StatisticFieldValue> buildFieldValuesResponse(String jsonResponse) throws JSONException {
    JSONObject json = new JSONObject(jsonResponse);
    JSONObject aggregations = json.has(AGGREGATIONS_RESPONSE_BODY) ? json.getJSONObject(AGGREGATIONS_RESPONSE_BODY) : null;
    if (aggregations == null) {
      return Collections.emptyList();
    }
    JSONObject result = aggregations.has(AGGREGATION_RESULT_PARAM) ? aggregations.getJSONObject(AGGREGATION_RESULT_PARAM) : null;
    if (result == null) {
      return Collections.emptyList();
    }
    JSONArray buckets = result.has(BUCKETS_RESPONSE_BODY) ? result.getJSONArray(BUCKETS_RESPONSE_BODY) : null;
    if (buckets == null) {
      return Collections.emptyList();
    }
    List<StatisticFieldValue> results = new ArrayList<>();
    for (int i = 0; i < buckets.length(); i++) {
      JSONObject bucket = buckets.getJSONObject(i);
      String value = getResultKeyAsString(bucket);
      long count = bucket.getLong("doc_count");
      results.add(new StatisticFieldValue(value, count));
    }
    return results;
  }

  private void addESDateSubField(String dateFieldName) {
    StatisticFieldMapping fieldMapping =
                                       new StatisticFieldMapping("doc['timestamp'].value." + dateFieldName, "long", false, true);
    esMappings.put(fieldMapping.getName(), fieldMapping);
  }

  private String buildAnalyticsQuery(List<AnalyticsAggregation> aggregations,
                                     List<AnalyticsFieldFilter> filters,
                                     ZoneId timeZone,
                                     long offset,
                                     long limit) {
    return buildAnalyticsQuery(aggregations, filters, null, false, timeZone, offset, limit);
  }

  private String buildAnalyticsQuery(List<AnalyticsAggregation> aggregations,
                                     List<AnalyticsFieldFilter> filters,
                                     AnalyticsAggregationType aggregationType,
                                     boolean hasLimitAggregation,
                                     ZoneId timeZone,
                                     long offset,
                                     long limit) {
    boolean isCount = aggregations != null;

    StringBuilder esQuery = new StringBuilder();
    esQuery.append("{");
    buildSearchFilterQuery(esQuery,
                           filters,
                           offset,
                           limit,
                           isCount);
    if (isCount) {
      buildAggregationQuery(esQuery, aggregations, aggregationType, timeZone, hasLimitAggregation);
    }
    esQuery.append("}");
    String esQueryString = esQuery.toString();
    esQueryString = fixJSONStringFormat(esQueryString);
    LOG.debug("ES query to compute chart data with aggregations :{}, filters :{} . Query: {}",
              aggregations,
              filters,
              esQueryString);
    return esQueryString;
  }

  private void buildSearchFilterQuery(StringBuilder esQuery,
                                      List<AnalyticsFieldFilter> filters,
                                      long offset,
                                      long limit,
                                      boolean isCount) {
    // If it's a count query, no need for results and no need for sort neither
    if (isCount) {
      esQuery.append("     \"size\" : 0");
    } else {
      if (offset > 0) {
        esQuery.append("     \"from\" : ").append(offset).append(",");
      }
      if (limit <= 0 || limit > Integer.MAX_VALUE) {
        limit = 10000;
      }
      esQuery.append("     \"size\" : ").append(limit).append(",");
      // Sort by date
      esQuery.append("     \"sort\" : [{ \"timestamp\":{\"order\" : \"desc\"}}]");
    }

    // Query body
    appendSearchFilterConditions(filters, esQuery);
    // End query body
  }

  private void appendSearchFilterConditions(List<AnalyticsFieldFilter> filters, StringBuilder esQuery) {
    if (filters == null) {
      filters = new ArrayList<>();
    } else {
      filters = new ArrayList<>(filters);
    }
    esQuery.append("""
        ,
        "query": {
          "bool" : {
            "must" : [
        """);
    for (AnalyticsFieldFilter fieldFilter : filters) {
      String esFieldName = fieldFilter.getField();
      StatisticFieldMapping fieldMapping = this.esMappings.get(esFieldName);
      if (fieldMapping != null) {
        esFieldName = fieldMapping.getAggregationFieldName();
      }

      String esQueryValue = fieldMapping == null ? StatisticFieldMapping.computeESQueryValue(fieldFilter.getValueString()) :
                                                 fieldMapping.getESQueryValue(fieldFilter.getValueString());
      switch (fieldFilter.getType()) {
      case NOT_NULL:
        esQuery.append("""
            {"exists" : {"field": "$name"}},
            """.replace(NAME_REQUEST_BODY_PARAM, esFieldName));
        break;
      case IS_NULL:
        esQuery.append("""
            {"bool" : {"must_not": {"exists": {"field": "$name"}}}},
            """.replace(NAME_REQUEST_BODY_PARAM, esFieldName));
        break;
      case EQUAL:
        esQuery.append("""
            {"match" : {"$name": $value}},
            """.replace(NAME_REQUEST_BODY_PARAM, esFieldName)
               .replace(VALUE_REQUEST_BODY_PARAM, esQueryValue));
        break;
      case NOT_EQUAL:
        esQuery.append("""
            {"bool": {"must_not": {"match" : {"$name": $value}}}},
            """.replace(NAME_REQUEST_BODY_PARAM, esFieldName)
               .replace(VALUE_REQUEST_BODY_PARAM, esQueryValue));
        break;
      case GREATER:
        esQuery.append("""
            {"range": {"$name": {"gte": $value}}},
            """.replace(NAME_REQUEST_BODY_PARAM, esFieldName)
               .replace(VALUE_REQUEST_BODY_PARAM, esQueryValue));
        break;
      case LESS:
        esQuery.append("""
            {"range": {"$name": {"lte": $value}}},
            """.replace(NAME_REQUEST_BODY_PARAM, esFieldName)
               .replace(VALUE_REQUEST_BODY_PARAM, esQueryValue));
        break;
      case RANGE:
        Range range = fieldFilter.getRange();
        esQuery.append("""
            {"range": {
              "$name": {
                "gte": $min,
                "lte": $max
              }
            }},
            """.replace(NAME_REQUEST_BODY_PARAM, esFieldName)
               .replace(MIN_REQUEST_BODY_PARAM, range.getMin())
               .replace(MAX_REQUEST_BODY_PARAM, range.getMax()));
        break;
      case IN_SET:
        esQuery.append("""
            {"terms": {"$name": $value}},
            """.replace(NAME_REQUEST_BODY_PARAM, esFieldName)
               .replace(VALUE_REQUEST_BODY_PARAM, collectionToJSONString(fieldFilter.getValueString())));
        break;
      case NOT_IN_SET:
        esQuery.append("""
            {"bool": {"must_not": {"terms": {"$name": $value}}}},
            """.replace(NAME_REQUEST_BODY_PARAM, esFieldName)
               .replace(VALUE_REQUEST_BODY_PARAM, collectionToJSONString(fieldFilter.getValueString())));
        break;
      default:
        break;
      }
    }
    esQuery.append("""
                ],
              },
            },
        """);
  }

  private void buildAggregationQuery(StringBuilder esQuery,
                                     List<AnalyticsAggregation> aggregations,
                                     AnalyticsAggregationType percentageAggregationType,
                                     ZoneId timeZone,
                                     boolean hasLimitAggregation) {
    if (aggregations != null && !aggregations.isEmpty()) {
      StringBuffer endOfQuery = new StringBuffer();
      int aggregationsSize = aggregations.size();
      for (int i = 0; i < aggregationsSize; i++) {
        AnalyticsAggregation aggregation = aggregations.get(i);

        AnalyticsAggregationType aggregationType = aggregation.getType();
        if (aggregationType.isUseInterval() && StringUtils.isBlank(aggregation.getInterval())) {
          throw new IllegalStateException("Analytics aggregation type '" + aggregationType +
              "' is using intervals while it has empty interval");
        }

        String fieldName = getAggregationFieldName(aggregationType);
        long limit = aggregation.getLimit();
        if (aggregationType.isUseLimit() && limit <= 0) {
          limit = aggregationReturnedDocumentsSize;
        }

        String aggregationFieldName = aggregation.getField();
        StatisticFieldMapping aggregationField = getFieldMapping(aggregationFieldName);

        appendStartAggregationFieldQuery(esQuery, aggregationType, fieldName);
        { // NOSONAR
          appendAggregationNameQuery(esQuery, aggregationFieldName, aggregationField);
          appendIntervalQuery(esQuery, timeZone, aggregation, aggregationType, aggregationFieldName);
          appendLimitQuery(esQuery, aggregationType, limit);
          appendSortQuery(esQuery, aggregations, aggregationsSize, i, aggregation, aggregationType);
        }
        appendEndAggregationFieldQuery(esQuery);

        // Appended at the end
        appendEndOfAggregations(endOfQuery, percentageAggregationType, hasLimitAggregation, aggregationsSize, i);
      }
      esQuery.append(endOfQuery);
    }
  }

  private void appendStartAggregationFieldQuery(StringBuilder esQuery,
                                                AnalyticsAggregationType aggregationType,
                                                String fieldName) {
    esQuery.append("""
        ,
         "aggs": {
           "$name": {
             "$aggName": {

        """.replace(NAME_REQUEST_BODY_PARAM, fieldName)
           .replace(AGG_NAME_REQUEST_BODY_PARAM, aggregationType.getAggName()));
  }

  private void appendAggregationNameQuery(StringBuilder esQuery,
                                          String aggregationFieldName,
                                          StatisticFieldMapping aggregationField) {
    if (aggregationField == null || !aggregationField.isScriptedField()) {
      esQuery.append("""
                    "field": "$aggFieldName"
          """.replace(AGG_FIELD_NAME_REQUEST_BODY_PARAM, aggregationFieldName));
    } else {
      esQuery.append("""
                   "script": {
                     "lang": "painless",
                     "source": "$aggFieldName"
                   }
          """.replace(AGG_FIELD_NAME_REQUEST_BODY_PARAM, aggregationFieldName));
    }
  }

  private void appendIntervalQuery(StringBuilder esQuery,
                                   ZoneId timeZone,
                                   AnalyticsAggregation aggregation,
                                   AnalyticsAggregationType aggregationType,
                                   String aggregationFieldName) {
    if (aggregationType.isUseInterval()) {
      appendIntervalTypeQuery(esQuery, aggregation);
      appendIntervalOffsetQuery(esQuery, aggregation);
      appendIntervalTimeZoneQuery(esQuery, timeZone, aggregationFieldName);
      appendIntervalBoundQuery(esQuery, aggregation);
    }
  }

  private void appendIntervalTypeQuery(StringBuilder esQuery, AnalyticsAggregation aggregation) {
    if (AnalyticsAggregation.ALL_INTERVALS.contains(aggregation.getInterval())) {
      esQuery.append("""
                  ,
                  "calendar_interval": "$interval"
          """.replace(INTERVAL_REQUEST_BODY_PARAM, aggregation.getInterval()));
    } else {
      esQuery.append("""
                  ,
                  "fixed_interval": "$interval"
          """.replace(INTERVAL_REQUEST_BODY_PARAM, aggregation.getInterval()));
    }
  }

  private void appendIntervalOffsetQuery(StringBuilder esQuery, AnalyticsAggregation aggregation) {
    if (aggregation.getOffset() != null) {
      esQuery.append("""
                 ,
                  "offset": "$intervalOffset"
          """.replace(INTERVAL_OFFSET_REQUEST_BODY_PARAM, aggregation.getOffset()));
    }
  }

  private void appendIntervalTimeZoneQuery(StringBuilder esQuery, ZoneId timeZone, String aggregationFieldName) {
    if (timeZone != null
        && !ZoneOffset.UTC.equals(timeZone)
        && aggregationFieldName.equals(FIELD_TIMESTAMP)) {
      esQuery.append("""
                 ,
                  "time_zone": "$timeZoneId"
          """.replace(TIME_ZONE_ID_REQUEST_BODY_PARAM, timeZone.getId()));
    }
  }

  private void appendIntervalBoundQuery(StringBuilder esQuery, AnalyticsAggregation aggregation) {
    if (aggregation.isUseBounds()) {
      esQuery.append("""
                  ,
                  "min_doc_count": 0,
                  "extended_bounds": {
                     "min": $minBound,
                     "max": $maxBound
                  }
          """.replace(MIN_BOUND_REQUEST_BODY_PARAM, String.valueOf(aggregation.getMinBound()))
             .replace(MAX_BOUND_REQUEST_BODY_PARAM, String.valueOf(aggregation.getMaxBound())));
    }
  }

  private void appendLimitQuery(StringBuilder esQuery, AnalyticsAggregationType aggregationType, long limit) {
    if (aggregationType.isUseLimit() && limit > 0) {
      esQuery.append("""
                   ,
                    "size": $size
          """.replace(SIZE_REQUEST_BODY_PARAM, String.valueOf(limit)));
    }
  }

  private void appendSortQuery(StringBuilder esQuery,
                               List<AnalyticsAggregation> aggregations,
                               int aggregationsSize,
                               int i,
                               AnalyticsAggregation aggregation,
                               AnalyticsAggregationType aggregationType) {
    if (aggregationType.isUseSort()) {
      String sortField = null;
      if ((i + 1) < aggregationsSize) {
        AnalyticsAggregation nextAggregation = aggregations.get(i + 1);
        sortField = getSortField(nextAggregation);
      } else if (aggregationType == AnalyticsAggregationType.TERMS) {
        sortField = "_count";
      }
      if (sortField != null) {
        esQuery.append("""
                     ,
                      "order": {"$sortField": "$sortDirection"}
            """.replace(SORT_FIELD_REQUEST_BODY_PARAM, sortField)
               .replace(SORT_DIRECTION_REQUEST_BODY_PARAM, aggregation.getSortDirection()));
      }
    }
  }

  private void appendEndAggregationFieldQuery(StringBuilder esQuery) {
    esQuery.append("""
                 }
        """); // End $aggName
  }

  private void appendEndOfAggregations(StringBuffer endOfQuery,
                                       AnalyticsAggregationType percentageAggregationType,
                                       boolean hasLimitAggregation,
                                       int aggregationsSize,
                                       int i) {
    endOfQuery.append("""
                 }
        """); // End $name
    if (hasLimitAggregation && percentageAggregationType != null) {
      if (i != (aggregationsSize - 1)) {
        endOfQuery.append("     }");
      }
      if (i == (aggregationsSize - 2)) {
        endOfQuery.append("""
            },
            "aggregation_buckets_value": {
              "$bucketAggregationType": {"buckets_path": "aggregation_result>aggregation_result_value.value"}
            }
            """.replace("$bucketAggregationType", getBucketAggregationType(percentageAggregationType)));
      }
    } else {
      endOfQuery.append("     }");
    }
  }

  private String getAggregationFieldName(AnalyticsAggregationType aggregationType) {
    String fieldName = null;
    if (AnalyticsAggregationType.TERMS == aggregationType
        || AnalyticsAggregationType.DATE == aggregationType
        || AnalyticsAggregationType.HISTOGRAM == aggregationType) {
      fieldName = AGGREGATION_RESULT_PARAM;
    } else {
      fieldName = AGGREGATION_RESULT_VALUE_PARAM;
    }
    return fieldName;
  }

  private String getSortField(AnalyticsAggregation nextAggregation) {
    if (nextAggregation == null) {
      return null;
    }

    if (nextAggregation.getType().isNumericResult()) {
      return getAggregationFieldName(nextAggregation.getType()) + ".value";
    }
    return "_count";
  }

  private void computePercentageLimits(AnalyticsPercentageFilter percentageFilter,
                                       PercentageChartResult percentageChartResult,
                                       AnalyticsPeriod currentPeriod,
                                       AnalyticsPeriod previousPeriod) {
    AnalyticsPercentageLimit percentageLimit = percentageFilter.getPercentageLimit();
    PercentageChartValue chartValue = computePercentageChartData(percentageFilter.computeLimitFilter(),
                                                                 currentPeriod,
                                                                 previousPeriod,
                                                                 false);

    double currentPeriodLimit = chartValue.getCurrentPeriodValue();
    double previousPeriodLimit = chartValue.getPreviousPeriodValue();
    percentageChartResult.setCurrentPeriodLimit(currentPeriodLimit);
    percentageChartResult.setPreviousPeriodLimit(previousPeriodLimit);

    percentageFilter.setCurrentPeriodLimit(Math.round(currentPeriodLimit * percentageLimit.getPercentage() / 100));
    percentageFilter.setPreviousPeriodLimit(Math.round(previousPeriodLimit * percentageLimit.getPercentage() / 100));
  }

  private void computePercentageValuesPerPeriod(PercentageChartResult percentageResult,
                                                AnalyticsFilter analyticsFilter,
                                                AnalyticsPeriod currentPeriod,
                                                AnalyticsPeriod previousPeriod,
                                                boolean isValue,
                                                boolean hasLimitAggregation) {
    PercentageChartValue chartValue = computePercentageChartData(analyticsFilter,
                                                                 currentPeriod,
                                                                 previousPeriod,
                                                                 hasLimitAggregation);
    if (isValue) {
      if (chartValue.getCurrentPeriodValue() > 0) {
        percentageResult.setCurrentPeriodValue(chartValue.getCurrentPeriodValue());
      }
      if (chartValue.getPreviousPeriodValue() > 0) {
        percentageResult.setPreviousPeriodValue(chartValue.getPreviousPeriodValue());
      }
    } else {
      percentageResult.setCurrentPeriodThreshold(chartValue.getCurrentPeriodValue());
      percentageResult.setPreviousPeriodThreshold(chartValue.getPreviousPeriodValue());
    }
    percentageResult.setComputingTime(percentageResult.getComputingTime() + chartValue.getComputingTime());
    percentageResult.setDataCount(percentageResult.getDataCount() + chartValue.getDataCount());
  }

  private TableColumnResult buildTableColumnDataFromESResponse(TableColumnResult tableColumnResult, // NOSONAR
                                                               AnalyticsTableFilter tableFilter,
                                                               AnalyticsPeriod period,
                                                               AnalyticsPeriodType periodType,
                                                               int columnIndex,
                                                               boolean isValue,
                                                               String jsonResponse,
                                                               long limit) throws JSONException {
    if (tableColumnResult == null) {
      tableColumnResult = new TableColumnResult();
    }
    JSONObject json = new JSONObject(jsonResponse);
    JSONObject aggregations = json.has(AGGREGATIONS_RESPONSE_BODY) ? json.getJSONObject(AGGREGATIONS_RESPONSE_BODY) : null;
    if (aggregations == null) {
      return tableColumnResult;
    }
    JSONObject result = aggregations.has(AGGREGATION_RESULT_PARAM) ? aggregations.getJSONObject(AGGREGATION_RESULT_PARAM) : null;
    if (result == null) {
      return tableColumnResult;
    }
    JSONArray buckets = result.has(BUCKETS_RESPONSE_BODY) ? result.getJSONArray(BUCKETS_RESPONSE_BODY) : null;
    if (buckets == null) {
      return tableColumnResult;
    }
    AnalyticsTableColumnFilter columnFilter = tableFilter.getColumnFilter(columnIndex);
    LinkedHashMap<String, TableColumnItemValue> itemValues = new LinkedHashMap<>();
    tableColumnResult.getItems().forEach(item -> itemValues.put(item.getKey(), item));
    if (columnFilter.isPreviousPeriod()) {
      AnalyticsPeriod currentPeriod = tableFilter.getCurrentPeriod(period, periodType);
      AnalyticsPeriod previousPeriod = tableFilter.getPreviousPeriod(period, periodType);
      for (int i = buckets.length() - 1; i >= 0; i--) {
        JSONObject bucket = buckets.getJSONObject(i);
        long timestamp = bucket.getLong("key");
        boolean isCurrent = currentPeriod.isInPeriod(timestamp);
        if (!isCurrent && !previousPeriod.isInPeriod(timestamp)) {
          continue;
        }
        if (bucket.has(AGGREGATION_RESULT_PARAM) && bucket.getJSONObject(AGGREGATION_RESULT_PARAM).has(BUCKETS_RESPONSE_BODY)) {
          JSONArray subBuckets = bucket.getJSONObject(AGGREGATION_RESULT_PARAM).getJSONArray(BUCKETS_RESPONSE_BODY);
          for (int j = 0; j < subBuckets.length(); j++) {
            JSONObject subBucket = subBuckets.getJSONObject(j);
            String key = getResultKeyAsString(subBucket);
            TableColumnItemValue itemValue = itemValues.computeIfAbsent(key, mapKey -> new TableColumnItemValue());
            itemValue.setKey(key);
            if (columnIndex == 0) {
              itemValue.setValue(key);
            } else {
              computeColumnItemValue(itemValue, subBucket, isCurrent, isValue);
            }
          }
        }
      }
    } else {
      for (int i = 0; i < buckets.length(); i++) {
        JSONObject bucket = buckets.getJSONObject(i);
        String key = getResultKeyAsString(bucket);
        TableColumnItemValue itemValue = new TableColumnItemValue();
        itemValue.setKey(key);
        if (columnIndex == 0) {
          itemValue.setValue(key);
        } else {
          computeColumnItemValue(itemValue, bucket, true, isValue);
        }
        itemValues.put(itemValue.getKey(), itemValue);
      }
    }
    List<TableColumnItemValue> itemsList = null;
    if (limit > 0) {
      itemsList = itemValues.values().stream().limit(limit).toList();
    } else {
      itemsList = new ArrayList<>(itemValues.values());
    }
    tableColumnResult.setItems(itemsList);
    return tableColumnResult;
  }

  private void computeColumnItemValue(TableColumnItemValue itemValue,
                                      JSONObject bucket,
                                      boolean isCurrent,
                                      boolean isValue) throws JSONException {
    Object value;
    if (bucket.has(AGGREGATION_RESULT_VALUE_PARAM)) {
      value = bucket.getJSONObject(AGGREGATION_RESULT_VALUE_PARAM).get(VALUE_PARAM);
    } else if (bucket.has(AGGREGATION_RESULT_PARAM)) {
      JSONObject subAggregationResult = bucket.getJSONObject(AGGREGATION_RESULT_PARAM);
      List<String> values = new ArrayList<>();
      if (subAggregationResult.has(BUCKETS_RESPONSE_BODY)) {
        JSONArray subAggregationBuckets = subAggregationResult.getJSONArray(BUCKETS_RESPONSE_BODY);
        for (int j = 0; j < subAggregationBuckets.length(); j++) {
          JSONObject subAggregationBucket = subAggregationBuckets.getJSONObject(j);
          values.add(getResultKeyAsString(subAggregationBucket));
        }
      }
      value = values;
    } else if (bucket.has(VALUE_PARAM)) {
      value = bucket.get(VALUE_PARAM);
    } else {
      value = null;
    }
    if (isValue) {
      if (isCurrent) {
        itemValue.setValue(toString(value));
      } else {
        itemValue.setPreviousValue(toString(value));
      }
    } else {
      if (isCurrent) {
        itemValue.setThreshold(toString(value));
      } else {
        itemValue.setPreviousThreshold(toString(value));
      }
    }
  }

  private PercentageChartValue buildPercentageChartValuesFromESResponse(String jsonResponse, // NOSONAR
                                                                        AnalyticsPeriod currentPeriod,
                                                                        AnalyticsPeriod previousPeriod) throws JSONException {
    PercentageChartValue percentageChartValue = new PercentageChartValue();
    JSONObject json = new JSONObject(jsonResponse);
    JSONObject aggregations = json.getJSONObject(AGGREGATIONS_RESPONSE_BODY);
    if (aggregations == null) {
      return percentageChartValue;
    }
    percentageChartValue.setComputingTime(json.getLong("took"));

    JSONObject hitsResult = json.getJSONObject("hits");
    percentageChartValue.setDataCount(hitsResult.getJSONObject("total").getLong(VALUE_PARAM));

    if (aggregations.has(AGGREGATION_BUCKETS_VALUE_PARAM)) {
      String value = toString(aggregations.getJSONObject(AGGREGATION_BUCKETS_VALUE_PARAM).get(VALUE_PARAM));
      double valueDouble = StringUtils.isBlank(value) || StringUtils.equals("null", value) ? 0d : Double.parseDouble(value);
      if (currentPeriod != null) {
        percentageChartValue.setCurrentPeriodValue(valueDouble);
      } else if (previousPeriod != null) {
        percentageChartValue.setPreviousPeriodValue(valueDouble);
      }
    } else if (aggregations.has(AGGREGATION_RESULT_PARAM)) {
      JSONArray buckets = aggregations.getJSONObject(AGGREGATION_RESULT_PARAM).getJSONArray(BUCKETS_RESPONSE_BODY);
      Map<Long, Double> values = new HashMap<>();
      if (buckets.length() > 0) {
        for (int i = 0; i < buckets.length(); i++) {
          JSONObject bucket = buckets.getJSONObject(i);
          Long timestamp = bucket.getLong("key");
          if (bucket.has(AGGREGATION_BUCKETS_VALUE_PARAM)) {
            String value = toString(bucket.getJSONObject(AGGREGATION_BUCKETS_VALUE_PARAM).get(VALUE_PARAM));
            values.put(timestamp,
                       StringUtils.isBlank(value) || StringUtils.equals("null", value) ? 0d : Double.parseDouble(value));
          } else if (bucket.has(AGGREGATION_RESULT_VALUE_PARAM)) {
            String value = toString(bucket.getJSONObject(AGGREGATION_RESULT_VALUE_PARAM).get(VALUE_PARAM));
            values.put(timestamp,
                       StringUtils.isBlank(value) || StringUtils.equals("null", value) ? 0d : Double.parseDouble(value));
          } else {
            LOG.warn("Can't extract value from bucket {}", bucket);
          }
        }

        if (currentPeriod != null) {
          double currentPeriodValue = values.entrySet()
                                            .stream()
                                            .filter(aggregationValue -> {
                                              long timestamp = aggregationValue.getKey();
                                              return timestamp < currentPeriod.getToInMS()
                                                     && timestamp >= currentPeriod.getFromInMS();

                                            })
                                            .map(Map.Entry::getValue)
                                            .findFirst()
                                            .orElse(0d);
          percentageChartValue.setCurrentPeriodValue(currentPeriodValue);
        }

        if (previousPeriod != null) {
          double previousPeriodValue = values.entrySet()
                                             .stream()
                                             .filter(aggregationValue -> {
                                               long timestamp = aggregationValue.getKey();
                                               return timestamp < previousPeriod.getToInMS()
                                                      && timestamp >= previousPeriod.getFromInMS();

                                             })
                                             .map(Map.Entry::getValue)
                                             .findFirst()
                                             .orElse(0d);
          percentageChartValue.setPreviousPeriodValue(previousPeriodValue);
        }
      }
    }
    return percentageChartValue;
  }

  private ChartDataList buildChartDataFromESResponse(AnalyticsFilter filter, String jsonResponse) throws JSONException {
    AnalyticsAggregation multipleChartsAggregation = filter.getMultipleChartsAggregation();
    String lang = filter.getLang();

    ChartDataList chartsData = new ChartDataList(lang);
    JSONObject json = new JSONObject(jsonResponse);
    JSONObject aggregations = json.getJSONObject(AGGREGATIONS_RESPONSE_BODY);
    if (aggregations == null) {
      return chartsData;
    }
    JSONObject hitsResult = (JSONObject) json.get("hits");

    chartsData.setComputingTime(json.getLong("took"));
    chartsData.setDataCount(hitsResult.getJSONObject("total").getLong(VALUE_PARAM));

    int level = multipleChartsAggregation == null ? 0 : -1;
    computeAggregatedResultEntry(filter, aggregations, chartsData, multipleChartsAggregation, null, null, level);
    addEmptyResultsToNotExistingEntries(chartsData);
    return chartsData;
  }

  private void computeAggregatedResultEntry(AnalyticsFilter filter, // NOSONAR
                                            JSONObject aggregations,
                                            ChartDataList chartsData,
                                            AnalyticsAggregation multipleChartsAggregation,
                                            ChartAggregationValue parentAggregation,
                                            ArrayList<ChartAggregationValue> aggregationValues,
                                            int level) throws JSONException {
    String lang = filter.getLang();

    JSONObject aggsResult = aggregations.getJSONObject(AGGREGATION_RESULT_PARAM);
    JSONArray buckets = aggsResult.getJSONArray(BUCKETS_RESPONSE_BODY);
    if (buckets.length() > 0) {
      int nextLevel = level + 1;
      for (int i = 0; i < buckets.length(); i++) {
        JSONObject bucketResult = buckets.getJSONObject(i);
        ArrayList<ChartAggregationValue> childAggregationValues = new ArrayList<>();
        if (aggregationValues != null) {
          childAggregationValues.addAll(aggregationValues);
        }

        if (bucketResult.isNull(AGGREGATION_RESULT_PARAM)) {
          // Final result is found: last element in term of depth of
          // aggregations
          String key = getResultKeyAsString(bucketResult);
          String result = null;
          if (bucketResult.isNull(AGGREGATION_RESULT_VALUE_PARAM)) {
            Object docCount = bucketResult.get("doc_count");
            result = docCount.toString();
          } else {
            JSONObject valueResult = bucketResult.getJSONObject(AGGREGATION_RESULT_VALUE_PARAM);
            Object value = valueResult.get(VALUE_PARAM);
            if (value instanceof BigDecimal bd) {
              result = bd.toPlainString();
            } else {
              result = value.toString();
            }
          }
          addAggregationValue(key, filter, childAggregationValues, level);

          List<String> labels = childAggregationValues.stream()
                                                      .map(ChartAggregationValue::getFieldLabel)
                                                      .toList();
          String label = StringUtils.join(labels, AGGREGATION_KEYS_SEPARATOR);

          ChartAggregationLabel chartLabel = new ChartAggregationLabel(childAggregationValues, label, lang);
          ChartAggregationResult aggregationResult = new ChartAggregationResult(chartLabel, chartLabel.getLabel(), result);

          chartsData.addAggregationResult(filter, parentAggregation, aggregationResult);
        } else {
          // An aggresgation in the middle of aggregations tree
          String key = getResultKeyAsString(bucketResult);
          ChartAggregationValue parentAggregationToUse = parentAggregation;
          if (multipleChartsAggregation != null && level == -1) {
            String fieldLabel;
            if (multipleChartsAggregation.getType() == AnalyticsAggregationType.DATE) {
              fieldLabel = multipleChartsAggregation.getLabel(key, filter.zoneId(), filter.getLang());
            } else if (filter.isHideLabel()) {
              fieldLabel = key;
            } else {
              fieldLabel = multipleChartsAggregation.getLabel(key);
            }
            parentAggregationToUse = new ChartAggregationValue(multipleChartsAggregation, key, fieldLabel);
          } else {
            addAggregationValue(key, filter, childAggregationValues, level);
          }

          computeAggregatedResultEntry(filter,
                                       bucketResult,
                                       chartsData,
                                       multipleChartsAggregation,
                                       parentAggregationToUse,
                                       childAggregationValues,
                                       nextLevel);
        }
      }
    }
  }

  private void addAggregationValue(String key,
                                   AnalyticsFilter filter,
                                   ArrayList<ChartAggregationValue> aggregationValues,
                                   int level) {
    AnalyticsAggregation aggregation = null;
    if (filter.getXAxisAggregations().size() < level) {
      if (filter.getYAxisAggregation() == null) {
        throw new IllegalStateException("Can't find relative aggregation to index " + level);
      }
      aggregation = filter.getYAxisAggregation();
    } else {
      aggregation = filter.getXAxisAggregations().get(level);
    }

    String fieldLabel = null;
    if (aggregation == null || filter.isHideLabel()) {
      fieldLabel = key;
    } else if (aggregation.getType() == AnalyticsAggregationType.DATE) {
      fieldLabel = aggregation.getLabel(key, filter.zoneId(), filter.getLang());
    } else {
      fieldLabel = aggregation.getLabel(key);
    }

    ChartAggregationValue aggregationValue = new ChartAggregationValue(aggregation, key, fieldLabel);
    aggregationValues.add(aggregationValue);
  }

  private List<StatisticData> buildSearchResultFromESResponse(String jsonResponse) throws JSONException {
    List<StatisticData> results = new ArrayList<>();
    JSONObject json = new JSONObject(jsonResponse);

    JSONObject jsonResult = (JSONObject) json.get("hits");
    if (jsonResult == null) {
      return results;
    }

    JSONArray jsonHits = jsonResult.getJSONArray("hits");
    for (int i = 0; i < jsonHits.length(); i++) {
      JSONObject statisticDataJsonObject = jsonHits.getJSONObject(i).getJSONObject("_source");
      int statusOrdinal = statisticDataJsonObject.getInt("status");
      statisticDataJsonObject.put("status", StatisticStatus.values()[statusOrdinal]);
      StatisticData statisticData = fromJsonString(statisticDataJsonObject.toString(), StatisticData.class);
      DEFAULT_FIELDS.stream().forEach(statisticDataJsonObject::remove);

      statisticData.setParameters(new HashMap<>());
      Iterator<?> remainingKeys = statisticDataJsonObject.keys();
      while (remainingKeys.hasNext()) {
        String key = (String) remainingKeys.next();
        statisticData.getParameters().put(key, toString(statisticDataJsonObject.get(key)));
      }

      results.add(statisticData);
    }
    return results;
  }

  private StatisticFieldMapping getFieldMapping(String fieldName) {
    return esMappings.get(fieldName);
  }

  private String getBucketAggregationType(AnalyticsAggregationType percentageAggregationType) {
    switch (percentageAggregationType) {
    case MIN:
      return "min_bucket";
    case MAX:
      return "max_bucket";
    case AVG:
      return "avg_bucket";
    default:
      return "sum_bucket";
    }
  }

  private void readFieldsMapping() {
    SettingValue<?> existingMapping = settingService.get(CONTEXT, ES_SCOPE, ES_AGGREGATED_MAPPING);
    if (existingMapping == null) {
      return;
    }

    String esMappingSerialized = existingMapping.getValue().toString();
    try {
      JSONObject jsonObject = new JSONObject(esMappingSerialized);
      @SuppressWarnings("rawtypes")
      Iterator keys = jsonObject.keys();
      while (keys.hasNext()) {
        String key = keys.next().toString();
        String fieldMappingString = toString(jsonObject.get(key));
        StatisticFieldMapping fieldMapping = fromJsonString(fieldMappingString, StatisticFieldMapping.class);
        esMappings.put(key, fieldMapping);
      }
    } catch (JSONException e) {
      LOG.error("Error reading ES mapped fields", e);
    }
  }

  private void storeFieldsMappings() throws JSONException {
    JSONObject jsonObject = new JSONObject();
    Set<String> keys = esMappings.keySet();
    for (String key : keys) {
      jsonObject.put(key, toJsonString(esMappings.get(key)));
    }
    settingService.set(CONTEXT,
                       ES_SCOPE,
                       ES_AGGREGATED_MAPPING,
                       SettingValue.create(jsonObject.toString()));
  }

  private void addEmptyResultsToNotExistingEntries(ChartDataList chartsData) {
    LinkedHashSet<ChartAggregationLabel> aggregationLabels = chartsData.getAggregationLabels();
    int index = 0;
    Iterator<ChartAggregationLabel> iterator = aggregationLabels.iterator();
    while (iterator.hasNext()) {
      ChartAggregationLabel chartAggregationLabel = iterator.next();
      // Placeholder result to add to charts not having results retrieved from
      // ES
      ChartAggregationResult emptyResult = new ChartAggregationResult(chartAggregationLabel,
                                                                      chartAggregationLabel.getLabel(),
                                                                      null);
      LinkedHashSet<ChartData> charts = chartsData.getCharts();
      for (ChartData chartData : charts) {
        // Add empty result if not exists on chart at exact same index
        chartData.addAggregationResult(emptyResult, index, false);
      }
      index++;
    }
  }

  private String getResultKeyAsString(JSONObject bucketResult) throws JSONException {
    return bucketResult.has("key_as_string") ? bucketResult.getString("key_as_string") : toString(bucketResult.get("key"));
  }

  private String toString(Object value) {
    return Objects.toString(value, null);
  }

}
