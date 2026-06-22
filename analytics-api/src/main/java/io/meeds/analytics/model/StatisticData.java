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
package io.meeds.analytics.model;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.utils.PropertyManager;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class StatisticData implements Serializable {

  private static final ZoneId             DEFAULT_ZONE_ID  = ZoneId.systemDefault();

  private static final boolean            DEVELOPPING      = PropertyManager.isDevelopping();

  private static final List<Class<?>>     PRIMITIVE_TYPES  = List.of(Boolean.class,
                                                                     Byte.class,
                                                                     Short.class,
                                                                     Integer.class,
                                                                     Long.class,
                                                                     Float.class,
                                                                     Double.class);

  private static final List<Class<?>>     DATE_TYPES       = List.of(Date.class,
                                                                     Calendar.class,
                                                                     Instant.class,
                                                                     ZonedDateTime.class,
                                                                     OffsetDateTime.class,
                                                                     LocalDateTime.class,
                                                                     LocalDate.class);

  private static final long               serialVersionUID = -2660993500359866340L;

  private static final String             HASH_ALGORITHM   = "SHA-256";

  private final String                    uuid             = UUID.randomUUID().toString();

  @ToString.Exclude
  private transient volatile Long         computedId;

  @ToString.Exclude
  private DateFormat                      dateFormat;

  private long                            timestamp;

  private long                            userId;

  private long                            spaceId;

  private String                          module;

  private String                          subModule;

  private String                          operation;

  private StatisticStatus                 status           = StatisticStatus.OK;

  private String                          errorMessage;

  private long                            duration;

  private long                            errorCode;

  private Map<String, Object>             parameters;                                        // NOSONAR

  private Map<String, Collection<Object>> listParameters;                                    // NOSONAR

  public enum StatisticStatus {
    OK, KO;
  }

  @Override
  public boolean equals(final Object o) {
    return this == o || o instanceof StatisticData other && computeId() == other.computeId();
  }

  @Override
  public int hashCode() {
    return Long.hashCode(computeId());
  }

  /**
   * Add a textual statistic data parameter.
   *
   * @param key Statistic Field Name
   * @param value Statistic Field Value
   */
  public void addKeyword(String key, Object value) {
    if (value != null) {
      if (value instanceof Collection c) { // NOSONAR
        addKeywords(key, c);
      } else {
        addParameterValue(key, String.valueOf(value));
      }
    }
  }

  /**
   * Add a textual statistic data list parameter.
   *
   * @param key Statistic Field Name
   * @param value Statistic Field Values
   */
  public void addKeywords(String key, Collection<?> value) {
    if (value == null) {
      return;
    }
    addListParameterValues(key,
                           value.stream()
                                .filter(Objects::nonNull)
                                .map(String::valueOf)
                                .toList());
  }

  /**
   * Add a long statistic data parameter. Used for fields used to aggregate
   * count, mean, min, max or for timestamp only
   *
   * @param key Statistic Field Name
   * @param value Statistic Field Value
   */
  public void addLong(String key, Object value) {
    if (value == null) {
      return;
    }
    if (value instanceof Collection c) { // NOSONAR
      addLongs(key, c);
      return;
    }
    Long longValue = parseLongValue(key, value);
    if (longValue != null) {
      addParameterValue(key, longValue);
    }
  }

  /**
   * Add a long statistic data list parameter. Used for fields used to aggregate
   * count, mean, min, max or for timestamp only
   *
   * @param key Statistic Field Name
   * @param value Statistic Field Values
   */
  public void addLongs(String key, Collection<?> value) {
    if (value != null) {
      addListParameterValues(key,
                             value.stream()
                                  .filter(Objects::nonNull)
                                  .map(v -> parseLongValue(key, v))
                                  .filter(Objects::nonNull)
                                  .toList());
    }
  }

  /**
   * Add a double statistic data parameter. Used for fields used to aggregate
   * count, mean, min or max only
   *
   * @param key Statistic Field Name
   * @param value Statistic Field Value
   */
  public void addDouble(String key, Object value) {
    if (value == null) {
      return;
    }
    if (value instanceof Collection c) { // NOSONAR
      addDoubles(key, c);
      return;
    }
    Double doubleValue = parseDoubleValue(key, value);
    if (doubleValue != null) {
      addParameterValue(key, doubleValue);
    }
  }

  /**
   * Add a double statistic data list parameter. Used for fields used to
   * aggregate count, mean, min or max only
   *
   * @param key Statistic Field Name
   * @param value Statistic Field Values
   */
  public void addDoubles(String key, Collection<?> value) {
    if (value != null) {
      addListParameterValues(key,
                             value.stream()
                                  .filter(Objects::nonNull)
                                  .map(v -> parseDoubleValue(key, v))
                                  .filter(Objects::nonNull)
                                  .toList());
    }
  }

  /**
   * Add a boolean statistic data parameter.
   *
   * @param key Statistic Field Name
   * @param value Statistic Field Value
   */
  public void addBoolean(String key, Object value) {
    if (value == null) {
      return;
    }
    if (value instanceof Collection c) { // NOSONAR
      addBooleans(key, c);
      return;
    }
    Boolean booleanValue = parseBooleanValue(key, value);
    if (booleanValue != null) {
      addParameterValue(key, booleanValue);
    }
  }

  /**
   * Add a boolean statistic data list parameter.
   *
   * @param key Statistic Field Name
   * @param value Statistic Field Values
   */
  public void addBooleans(String key, Collection<?> value) {
    if (value != null) {
      addListParameterValues(key,
                             value.stream()
                                  .filter(Objects::nonNull)
                                  .map(v -> parseBooleanValue(key, v))
                                  .filter(Objects::nonNull)
                                  .toList());
    }
  }

  public void addDate(String key, Object value) {
    addLong(key, value);
  }

  public void addDates(String key, Collection<?> value) {
    addLongs(key, value);
  }

  /**
   * Add a statistic data parameter
   * 
   * @param key Statistic Field Name
   * @param value Statistic Field Value
   * @deprecated Use addKeyword, addLong, addDouble, addBoolean, or their
   *             collection variants to avoid ambiguous ES mappings.
   */
  @Deprecated(since = "7.2.0")
  public void addParameter(String key, Object value) { // NOSONAR
    if (parameters == null) {
      parameters = new HashMap<>();
    }
    if (value == null) {
      return;
    }
    if (DEVELOPPING || log.isDebugEnabled()) {
      log.warn("A statistic data field is collected using a deprecated API", new ProductOnlyStackTraceException());
    }
    if (value instanceof Collection) {
      Collection<?> collection = (Collection<?>) value;
      Collection<Object> values = collection.stream()
                                            .filter(Objects::nonNull)
                                            .map(this::getFieldValue)
                                            .toList();
      if (listParameters == null) {
        listParameters = new HashMap<>();
      }
      listParameters.put(key, values);
    } else {
      parameters.put(key, getFieldValue(value));
    }
    resetComputedId();
  }

  public long computeId() {
    Long localComputedId = computedId;
    if (localComputedId == null) {
      localComputedId = doComputeId();
      computedId = localComputedId;
    }
    return localComputedId;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
    resetComputedId();
  }

  public void setUserId(long userId) {
    this.userId = userId;
    resetComputedId();
  }

  public void setSpaceId(long spaceId) {
    this.spaceId = spaceId;
    resetComputedId();
  }

  public void setModule(String module) {
    this.module = module;
    resetComputedId();
  }

  public void setSubModule(String subModule) {
    this.subModule = subModule;
    resetComputedId();
  }

  public void setOperation(String operation) {
    this.operation = operation;
    resetComputedId();
  }

  public void setParameters(Map<String, Object> parameters) {
    this.parameters = parameters;
    resetComputedId();
  }

  public void setListParameters(Map<String, Collection<Object>> listParameters) {
    this.listParameters = listParameters;
    resetComputedId();
  }

  private long doComputeId() {
    byte[] digest = computeDigest(buildIdSource());
    return ((long) (digest[0] & 0xff) << 56)
        | ((long) (digest[1] & 0xff) << 48)
        | ((long) (digest[2] & 0xff) << 40)
        | ((long) (digest[3] & 0xff) << 32)
        | ((long) (digest[4] & 0xff) << 24)
        | ((long) (digest[5] & 0xff) << 16)
        | ((long) (digest[6] & 0xff) << 8)
        | ((long) (digest[7] & 0xff)); // NOSONAR
  }

  private String buildIdSource() {
    StringBuilder builder = new StringBuilder();
    appendIdPart(builder, timestamp);
    appendIdPart(builder, userId);
    appendIdPart(builder, spaceId);
    appendIdPart(builder, module);
    appendIdPart(builder, subModule);
    appendIdPart(builder, operation);
    appendMap(builder, parameters);
    appendMap(builder, listParameters);
    appendIdPart(builder, uuid);
    return builder.toString();
  }

  private void appendMap(StringBuilder builder, Map<String, ?> map) {
    if (map == null || map.isEmpty()) {
      appendIdPart(builder, null);
      return;
    }
    builder.append("map{");
    map.entrySet()
       .stream()
       .sorted(Entry.comparingByKey())
       .forEach(entry -> {
         appendIdPart(builder, entry.getKey());
         appendValue(builder, entry.getValue());
       });
    builder.append('}');
  }

  private void appendValue(StringBuilder builder, Object value) {
    if (value instanceof Collection<?> collection) {
      builder.append("collection[");
      collection.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .sorted()
                .forEach(item -> appendIdPart(builder, item));
      builder.append(']');
    } else {
      appendIdPart(builder, value);
    }
  }

  private void appendIdPart(StringBuilder builder, Object value) {
    String stringValue = String.valueOf(value);
    builder.append(stringValue.length())
           .append(':')
           .append(stringValue)
           .append('|');
  }

  private byte[] computeDigest(String value) {
    try {
      return MessageDigest.getInstance(HASH_ALGORITHM)
                          .digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Unable to compute statistic data identifier", e);
    }
  }

  private void resetComputedId() {
    computedId = null;
  }

  private Object getFieldValue(Object value) {
    if (value instanceof Date) {
      return buildDateFormat().format(value);
    } else if (value == null || isOfTypePrimitiveClass(value)) {
      return value;
    } else {
      return String.valueOf(value);
    }
  }

  private static boolean isOfTypePrimitiveClass(Object value) {
    return PRIMITIVE_TYPES.stream().anyMatch(c -> c.isAssignableFrom(value.getClass()));
  }

  private static boolean isDate(Object value) {
    return DATE_TYPES.stream().anyMatch(c -> c.isAssignableFrom(value.getClass()));
  }

  private DateFormat buildDateFormat() {
    if (dateFormat == null) {
      dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z");
    }
    return dateFormat;
  }

  private long toEpochMillis(Object value) {
    return switch (value) {
    case Date date -> date.getTime();
    case Calendar calendar -> calendar.getTimeInMillis();
    case Instant instant -> instant.toEpochMilli();
    case ZonedDateTime zonedDateTime -> zonedDateTime.toInstant().toEpochMilli();
    case OffsetDateTime offsetDateTime -> offsetDateTime.toInstant().toEpochMilli();
    case LocalDateTime localDateTime -> localDateTime.atZone(DEFAULT_ZONE_ID).toInstant().toEpochMilli();
    case LocalDate localDate -> localDate.atStartOfDay(DEFAULT_ZONE_ID).toInstant().toEpochMilli();
    default -> Long.parseLong(String.valueOf(value));
    };
  }

  private void addParameterValue(String key, Object value) {
    if (StringUtils.isBlank(key) || value == null) {
      return;
    }
    if (parameters == null) {
      parameters = new HashMap<>();
    }
    parameters.put(key, value);
    resetComputedId();
  }

  private void addListParameterValues(String key, Collection<?> value) {
    if (StringUtils.isBlank(key) || value == null) {
      return;
    }
    Collection<Object> values = value.stream()
                                     .filter(Objects::nonNull)
                                     .map(Object.class::cast)
                                     .toList();
    if (values.isEmpty()) {
      return;
    }
    if (listParameters == null) {
      listParameters = new HashMap<>();
    }
    listParameters.put(key, values);
    resetComputedId();
  }

  private Long parseLongValue(String key, Object value) {
    try {
      return isDate(value) ? toEpochMillis(value) : Long.parseLong(String.valueOf(value));
    } catch (RuntimeException e) {
      logInvalidTypedValue(key, value, "long");
      return null;
    }
  }

  private Double parseDoubleValue(String key, Object value) {
    try {
      return Double.parseDouble(String.valueOf(value));
    } catch (RuntimeException e) {
      logInvalidTypedValue(key, value, "double");
      return null;
    }
  }

  private Boolean parseBooleanValue(String key, Object value) {
    String stringValue = String.valueOf(value);
    if (StringUtils.equalsAnyIgnoreCase(stringValue, "true", "false")) {
      return Boolean.parseBoolean(stringValue);
    }
    logInvalidTypedValue(key, value, "boolean");
    return null; // NOSONAR
  }

  private void logInvalidTypedValue(String key, Object value, String type) {
    if (DEVELOPPING || log.isDebugEnabled()) {
      log.warn("Invalid {} statistic value for field '{}' and value {}. Field will be ignored",
               type,
               key,
               value,
               new ProductOnlyStackTraceException());
    } else {
      log.warn("Invalid {} statistic value for field '{}' and value {}. Field will be ignored",
               type,
               key,
               value);
    }
  }

}
