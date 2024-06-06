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
package io.meeds.analytics.model.filter.aggregation;

import java.io.Serializable;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

import io.meeds.analytics.utils.AnalyticsUtils;

import groovy.transform.ToString;
import lombok.*;
import lombok.EqualsAndHashCode.Exclude;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsAggregation implements Serializable, Cloneable {

  private static final long             serialVersionUID       = 2130321038232532587L;

  public static final String            YEAR_INTERVAL          = "year";

  public static final String            MONTH_INTERVAL         = "month";

  public static final String            QUARTER_INTERVAL       = "quarter";

  public static final String            WEEK_INTERVAL          = "week";

  public static final String            DAY_INTERVAL           = "day";

  public static final String            HOUR_INTERVAL          = "hour";

  public static final String            MINUTE_INTERVAL        = "minute";

  public static final String            SECOND_INTERVAL        = "second";

  public static final DateTimeFormatter YEAR_DATE_FORMATTER    = DateTimeFormatter.ofPattern("uuuu");

  public static final DateTimeFormatter QUARTER_DATE_FORMATTER = DateTimeFormatter.ofPattern("QQQ uuuu");

  public static final DateTimeFormatter MONTH_DATE_FORMATTER   = DateTimeFormatter.ofPattern("MMM uuuu");

  public static final DateTimeFormatter DAY_DATE_FORMATTER     = DateTimeFormatter.ofPattern("d MMM uuuu");

  public static final DateTimeFormatter WEEK_DATE_FORMATTER    = DateTimeFormatter.ISO_WEEK_DATE;

  public static final DateTimeFormatter HOUR_DATE_FORMATTER    = DateTimeFormatter.ofPattern("hh a, d MMM uuuu");

  private AnalyticsAggregationType      type;

  private String                        field;

  @Exclude
  private String                        sortDirection;

  private String                        interval;

  @Exclude
  private String                        offset;

  @Exclude
  private long                          limit;

  @Exclude
  private boolean                       useBounds;

  @Exclude
  private long                          minBound;

  @Exclude
  private long                          maxBound;

  public AnalyticsAggregation(AnalyticsAggregationType type, String field, String sortDirection, String interval, long limit) {
    super();
    this.type = type;
    this.field = field;
    this.sortDirection = sortDirection;
    this.interval = interval;
    this.limit = limit;
  }

  public AnalyticsAggregation(String field) {
    this.field = field;
    this.type = AnalyticsAggregationType.TERMS;
  }

  public String getSortDirection() {
    if (sortDirection == null) {
      return type == AnalyticsAggregationType.DATE ? "asc" : "desc";
    }
    return sortDirection;
  }

  public String getLabel(String fieldValue, ZoneId zoneId, String lang) {
    long timestamp = Long.parseLong(fieldValue);
    return formatTime(timestamp, zoneId, lang);
  }

  public String getLabel(String fieldValue) {
    return AnalyticsUtils.compueLabel(field, fieldValue);
  }

  private String formatTime(long timestamp, ZoneId zoneId, String lang) {
    Locale userLocale = StringUtils.isBlank(lang) ? Locale.getDefault() : Locale.forLanguageTag(lang);
    LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zoneId);
    DateTimeFormatter dateFormatter = null;
    switch (interval) {
      case YEAR_INTERVAL:
        dateFormatter = YEAR_DATE_FORMATTER;
        break;
      case QUARTER_INTERVAL:
        dateFormatter = QUARTER_DATE_FORMATTER;
        break;
      case MONTH_INTERVAL:
        dateFormatter = MONTH_DATE_FORMATTER;
        break;
      case WEEK_INTERVAL:
        dateFormatter = WEEK_DATE_FORMATTER;
        break;
      case DAY_INTERVAL:
        dateFormatter = DAY_DATE_FORMATTER;
        break;
      case HOUR_INTERVAL:
        dateFormatter = HOUR_DATE_FORMATTER;
        break;
      default:
        dateFormatter = DAY_DATE_FORMATTER;
    }
    String dateFormated =  dateTime.format(dateFormatter.withLocale(userLocale));
    if (interval.equals(WEEK_INTERVAL)){
       String[] date = dateFormated.split("-");
       dateFormated = date[1] + "-" + date[0];
    }
    return dateFormated;
  }

  @Override
  public AnalyticsAggregation clone() { // NOSONAR
    return new AnalyticsAggregation(type, field, sortDirection, interval, offset, limit, useBounds, minBound, maxBound);
  }

}
