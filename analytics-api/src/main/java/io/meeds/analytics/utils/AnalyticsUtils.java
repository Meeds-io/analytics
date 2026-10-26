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
package io.meeds.analytics.utils;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.social.core.activity.model.ActivityStream;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.profileproperty.ProfilePropertyService;
import org.exoplatform.social.core.profileproperty.model.ProfilePropertySetting;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.ws.frameworks.json.impl.JsonDefaultHandler;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.exoplatform.ws.frameworks.json.impl.JsonGeneratorImpl;
import org.exoplatform.ws.frameworks.json.impl.JsonParserImpl;
import org.exoplatform.ws.frameworks.json.impl.ObjectBuilder;

import io.meeds.analytics.api.service.CollaborativeActivityService;
import io.meeds.analytics.api.service.StatisticDataQueueService;
import io.meeds.analytics.constant.StatisticParameterType;
import io.meeds.analytics.model.SpaceStatisticAttribute;
import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.StatisticData.StatisticStatus;
import io.meeds.analytics.model.StatisticFieldMapping;
import io.meeds.social.category.model.Category;
import io.meeds.social.category.model.CategoryObject;
import io.meeds.social.category.service.CategoryService;

public class AnalyticsUtils {

  private static final Log              LOG                              = ExoLogger.getLogger(AnalyticsUtils.class);

  private static ProfilePropertyService profilePropertyService;

  public static final short             MAX_BULK_DOCUMENTS               = 100;

  public static final String            VALUES_SEPARATOR                 = ",";

  /** The document id, written by the storage as a fixed field. */
  public static final String            FIELD_ID                         = "id";

  public static final String            FIELD_IS_ANALYTICS               = "isAnalytics";

  public static final String            FIELD_ERROR_MESSAGE              = "errorMessage";

  public static final String            FIELD_ERROR_CODE                 = "errorCode";

  public static final String            FIELD_STATUS                     = "status";

  public static final String            FIELD_OPERATION                  = "operation";

  public static final String            FIELD_SUB_MODULE                 = "subModule";

  public static final String            FIELD_MODULE                     = "module";

  public static final String            FIELD_SPACE_ID                   = "spaceId";

  public static final String            FIELD_DURATION                   = "duration";

  public static final String            FIELD_USER_ID                    = "userId";

  public static final String            FIELD_TIMESTAMP                  = "timestamp";

  /** The activity type a stream post or comment event carries as a parameter. */
  public static final String            FIELD_ACTIVITY_TYPE              = "activityType";

  public static final String            ALTERNATIVE_FIELD_SUFFIX         = "_alt";

  /**
   * Keyword parameter the websocket path stamps on every event it records, so
   * that a query tells a browser event from a server one; a server emitter
   * never sets it.
   */
  public static final String            STATISTIC_ORIGIN                 = "statisticOrigin";

  /** The one value of {@link #STATISTIC_ORIGIN}. */
  public static final String            STATISTIC_ORIGIN_WEBSOCKET       = "websocket";

  /**
   * The parameter names a page may never set on a websocket event: every
   * field the storage writes as a fixed field but the space, which the
   * listener resolves itself, the origin and the collaborative-activity field,
   * each with its alternative names.
   */
  public static final Pattern           RESERVED_PARAMETER_NAMES         =
                                                                 Pattern.compile("^(" + String.join("|",
                                                                                                    FIELD_ID,
                                                                                                    FIELD_TIMESTAMP,
                                                                                                    FIELD_USER_ID,
                                                                                                    FIELD_MODULE,
                                                                                                    FIELD_SUB_MODULE,
                                                                                                    FIELD_OPERATION,
                                                                                                    FIELD_STATUS,
                                                                                                    FIELD_ERROR_CODE,
                                                                                                    FIELD_ERROR_MESSAGE,
                                                                                                    FIELD_DURATION,
                                                                                                    FIELD_IS_ANALYTICS,
                                                                                                    STATISTIC_ORIGIN,
                                                                                                    CollaborativeActivityService.FIELD_NAME)
                                                                     + ")(" + ALTERNATIVE_FIELD_SUFFIX + "\\d*)?$");

  /**
   * Every parameter name carrying the storage's own alternative-name suffix,
   * which no page has a reason to send.
   */
  public static final Pattern           ALTERNATIVE_PARAMETER_NAMES      = Pattern.compile(".*" + ALTERNATIVE_FIELD_SUFFIX
      + "\\d*$");

  /**
   * The parameters of the space family a page may send, read once by the
   * websocket listener to resolve the space, then dropped.
   */
  public static final Pattern           SPACE_PARAMETER_NAMES            = Pattern.compile("^(" + FIELD_SPACE_ID
      + "|spacePrettyName)(" + ALTERNATIVE_FIELD_SUFFIX + "\\d*)?$");

  /** Default of {@code analytics.es.index.prefix}, the weekly index name prefix */
  public static final String            DEFAULT_INDEX_PREFIX             = "analytics";

  public static final int               MAX_ALTERNATIVE_FIELD_COUNT      = 4;

  public static final String            FIELD_MODIFIER_USER_SOCIAL_ID    = "modifierSocialId";

  public static final String            FIELD_SOCIAL_IDENTITY_ID         = "identityId";

  public static final String            LINK_ACTIVITY_TYPE               = "LINK_ACTIVITY";

  public static final String            FILE_SPACES_ACTIVITY_TYPE        = "files:spaces";

  public static final String            ACTIVITY_COMMENT                 = "comment";

  public static final String            PROFILE_PROPERTIES               = "profileProperties";

  public static final String            EXTERNAL_USER_SOURCE             = "externalUserSource";

  public static final String            PROPERTIES                       = "properties";

  public static final List<String>      COMPUTED_CHART_LABEL             = Arrays.asList(FIELD_MODIFIER_USER_SOCIAL_ID,                                 // NOSONAR
                                                                                         FIELD_SOCIAL_IDENTITY_ID,
                                                                                         FIELD_USER_ID,
                                                                                         FIELD_SPACE_ID);

  public static final DateTimeFormatter DATE_FORMATTER                   = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss");

  public static final List<String>      DEFAULT_FIELDS                   = Arrays.asList(FIELD_IS_ANALYTICS,                                            // NOSONAR
                                                                                         FIELD_ERROR_MESSAGE,
                                                                                         FIELD_ERROR_CODE,
                                                                                         FIELD_STATUS,
                                                                                         FIELD_OPERATION,
                                                                                         FIELD_MODULE,
                                                                                         FIELD_SUB_MODULE,
                                                                                         FIELD_SPACE_ID,
                                                                                         FIELD_USER_ID,
                                                                                         FIELD_TIMESTAMP);

  private static final Pattern          JSON_CLEANER_REPLACEMENT_PATTERN = Pattern.compile("([\\]}]+),([\\]}]+)");

  public static final DateTimeFormatter YEAR_WEEK                        = new DateTimeFormatterBuilder()
                                                                                                         .parseCaseInsensitive()
                                                                                                         .appendValue(IsoFields.WEEK_BASED_YEAR,
                                                                                                                      4,
                                                                                                                      10,
                                                                                                                      SignStyle.EXCEEDS_PAD)
                                                                                                         .appendLiteral("-W")
                                                                                                         .appendValue(IsoFields.WEEK_OF_WEEK_BASED_YEAR,
                                                                                                                      2)
                                                                                                         .optionalStart()
                                                                                                         .appendOffsetId()
                                                                                                         .toFormatter();

  public static final DateTimeFormatter YEAR_MONTH                       = new DateTimeFormatterBuilder()
                                                                                                         .appendValue(YEAR,
                                                                                                                      4,
                                                                                                                      10,
                                                                                                                      SignStyle.EXCEEDS_PAD)
                                                                                                         .appendLiteral('-')
                                                                                                         .appendValue(MONTH_OF_YEAR,
                                                                                                                      2)
                                                                                                         .toFormatter();

  public static final DateTimeFormatter YEAR_MONTH_DATE_HOUR             = new DateTimeFormatterBuilder()
                                                                                                         .appendValue(YEAR,
                                                                                                                      4,
                                                                                                                      10,
                                                                                                                      SignStyle.EXCEEDS_PAD)
                                                                                                         .appendLiteral('-')
                                                                                                         .appendValue(MONTH_OF_YEAR,
                                                                                                                      2)
                                                                                                         .appendLiteral('-')
                                                                                                         .appendValue(DAY_OF_MONTH,
                                                                                                                      2)
                                                                                                         .appendLiteral('T')
                                                                                                         .appendValue(HOUR_OF_DAY,
                                                                                                                      2)
                                                                                                         .toFormatter();

  private static final String           KEYWORD_FIELD_NAME_SUFFIX        = ".keyword";

  private AnalyticsUtils() {
  }

  public static final String getYearMonthDayHour(long timestamp) {
    LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
    return getYearMonthDayHour(date);
  }

  public static final String getYearMonthDayHour(LocalDateTime date) {
    return YEAR_MONTH_DATE_HOUR.format(date);
  }

  public static final String getYearMonthDay(long timestamp) {
    LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
    return getYearMonthDay(date);
  }

  public static final String getYearMonthDay(LocalDateTime date) {
    return DateTimeFormatter.ISO_LOCAL_DATE.format(date);
  }

  public static final String getYearMonth(long timestamp) {
    LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
    return getYearMonth(date);
  }

  public static final String getYearMonth(LocalDateTime date) {
    return YEAR_MONTH.format(date);
  }

  public static final String getYearWeek(long timestamp) {
    LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
    return getYearWeek(date);
  }

  public static final String getYearWeek(LocalDateTime date) {
    return YEAR_WEEK.format(date);
  }

  public static final String toJsonString(Object object) {
    try {
      if (object instanceof Collection) {
        return new JsonGeneratorImpl().createJsonArray((Collection<?>) object).toString();
      } else {
        return new JsonGeneratorImpl().createJsonObject(object).toString();
      }
    } catch (JsonException e) {
      throw new IllegalStateException("Error parsing object to string " + object, e);
    }
  }

  public static final String compueLabel(String chartKey, String chartValue) {
    String defaultLabel = (chartKey == null ? "" : chartKey.replace(KEYWORD_FIELD_NAME_SUFFIX, "") + "=") + chartValue;
    if (StringUtils.isBlank(chartKey) || StringUtils.isBlank(chartValue) || !COMPUTED_CHART_LABEL.contains(chartKey)) {
      return defaultLabel;
    }

    if (StringUtils.equals(chartKey, FIELD_SPACE_ID)) {
      SpaceService spaceService = CommonsUtils.getService(SpaceService.class);
      Space space = spaceService.getSpaceById(chartValue);
      if (space == null) {
        return defaultLabel;
      } else {
        return space.getDisplayName();
      }
    } else {
      IdentityManager identityManager = CommonsUtils.getService(IdentityManager.class);
      if (identityManager == null) {
        return defaultLabel;
      } else {
        Identity identity = getIdentity(chartValue);
        if (identity == null) {
          return defaultLabel;
        } else {
          return identity.getProfile().getFullName();
        }
      }
    }
  }

  public static final <T> T fromJsonString(String value, Class<T> resultClass) {
    try {
      if (StringUtils.isBlank(value)) {
        return null;
      }
      JsonDefaultHandler jsonDefaultHandler = new JsonDefaultHandler();
      new JsonParserImpl().parse(new ByteArrayInputStream(value.getBytes()), jsonDefaultHandler);
      return ObjectBuilder.createObject(resultClass, jsonDefaultHandler.getJsonObject());
    } catch (JsonException e) {
      throw new IllegalStateException("Error creating object from string : " + value, e);
    }
  }

  public static long timeToMilliseconds(LocalDateTime time) {
    return time.atZone(ZoneId.systemDefault()).toEpochSecond() * 1000;
  }

  public static final String fixJSONStringFormat(String jsonString) {
    do {
      jsonString = jsonString.replaceAll("([,\\]}][ \\t]+)", "$1")
                             .replaceAll(" +([,\\]}])", "$1")
                             .replace("\n", "")
                             .replaceAll(",+", VALUES_SEPARATOR)
                             .replaceAll("([\\]}]+),([\\]}]+)", "$1$2");
    } while (JSON_CLEANER_REPLACEMENT_PATTERN.matcher(jsonString).find());
    return jsonString;
  }

  public static final String collectionToJSONString(String value) {
    String[] valuesString = value.split(VALUES_SEPARATOR);
    List<String> valuesList = new ArrayList<>();
    Collections.addAll(valuesList, valuesString);
    return new JSONArray(valuesList.stream().map(String::trim).toList()).toString();
  }

  public static JsonNode getJsonNode(JsonNode jsonNode, int i, String... keys) { // NOSONAR
    if (keys == null || i >= keys.length) {
      return null;
    }
    try {
      if (keys[i] == null) {
        Iterator<String> fieldNames = jsonNode.fieldNames();
        if (!fieldNames.hasNext()) {
          return null;
        }
        ObjectNode resultNode = JsonNodeFactory.instance.objectNode();
        while (fieldNames.hasNext()) {
          String fieldName = fieldNames.next();
          JsonNode subNode = jsonNode.get(fieldName);
          JsonNode resultSubNode = getJsonNode(subNode, i + 1, keys);

          if (resultSubNode != null && resultSubNode.isObject()) {
            mergeObjectNodes(resultNode, (ObjectNode) resultSubNode);
          }
        }
        return resultNode;
      } else if (jsonNode.has(keys[i])) {
        JsonNode nextNode = jsonNode.get(keys[i]);
        i++;
        if (i == keys.length) {
          return nextNode;
        } else {
          return getJsonNode(nextNode, i, keys);
        }
      } else {
        return null;
      }
    } catch (Exception e) {
      LOG.warn("Error getting key object with {}", keys[i], e);
      return null;
    }
  }

  @ExoTransactional
  public static void addStatisticData(StatisticData statisticData) {
    if (statisticData == null || statisticData.getOperation() == null) {
      return;
    }
    if (statisticData.getTimestamp() <= 0) {
      statisticData.setTimestamp(System.currentTimeMillis());
    }
    if (statisticData.getStatus() == null) {
      statisticData.setStatus(StatisticStatus.OK);
    }
    if (statisticData.getUserId() == 0
        && ConversationState.getCurrent() != null
        && ConversationState.getCurrent().getIdentity() != null) {
      statisticData.setUserId(getIdentityId(ActivityStream.ORGANIZATION_PROVIDER_ID,
                                            ConversationState.getCurrent().getIdentity().getUserId()));
    }

    addUserStatistics(statisticData);

    try {
      StatisticDataQueueService analyticsQueueService = CommonsUtils.getService(StatisticDataQueueService.class);
      analyticsQueueService.put(statisticData);
    } catch (Exception e) {
      LOG.warn("Error adding analytics Queue entry: {}", statisticData, e);
    }
  }

  public static long getUserIdentityId(String username) {
    return getIdentityId(ActivityStream.ORGANIZATION_PROVIDER_ID, username);
  }

  public static Space getSpaceByPrettyName(String prettyName) {
    SpaceService spaceService = CommonsUtils.getService(SpaceService.class);
    return spaceService.getSpaceByPrettyName(prettyName);
  }

  public static Space getSpaceById(String spaceId) {
    SpaceService spaceService = CommonsUtils.getService(SpaceService.class);
    return spaceService.getSpaceById(spaceId);
  }

  public static Identity getIdentity(String identityIdString) {
    if (StringUtils.isBlank(identityIdString)) {
      return null;
    }
    IdentityManager identityManager = CommonsUtils.getService(IdentityManager.class);
    long identityId = parseId(identityIdString);
    return identityId > 0 ? identityManager.getIdentity(identityId) : null;
  }

  public static long getIdentityId(String identityId) {
    Identity identity = getIdentity(identityId);
    return identity == null ? 0 : Long.parseLong(identity.getId());
  }

  @ExoTransactional
  public static Identity getIdentity(String providerId, String remoteId) {
    if (StringUtils.isBlank(remoteId)) {
      return null;
    }
    IdentityManager identityManager = CommonsUtils.getService(IdentityManager.class);
    return identityManager.getOrCreateIdentity(providerId, remoteId);
  }

  public static long getIdentityId(String providerId, String remoteId) {
    Identity identity = getIdentity(providerId, remoteId);
    return identity == null ? 0 : Long.parseLong(identity.getId());
  }

  @ExoTransactional
  public static long getUserIdentityId(ConversationState currentState) {
    String username = getUsername(currentState);
    boolean unkownUser = isUnkownUser(username);
    if (unkownUser) {
      return 0;
    }
    IdentityManager identityManager = CommonsUtils.getService(IdentityManager.class);
    Identity identity = identityManager.getOrCreateIdentity(ActivityStream.ORGANIZATION_PROVIDER_ID, username);
    return identity == null ? 0 : Long.parseLong(identity.getId());
  }

  public static long getCurrentUserIdentityId() {
    ConversationState currentState = ConversationState.getCurrent();
    return getUserIdentityId(currentState);
  }

  public static boolean isUnkownUser(String username) {
    return username == null || StringUtils.equals(username, IdentityConstants.ANONIM)
           || StringUtils.equals(username, IdentityConstants.SYSTEM);
  }

  public static String getUsername(ConversationState currentState) {
    return currentState == null || currentState.getIdentity() == null
           || currentState.getIdentity().getUserId() == null ? null : currentState.getIdentity().getUserId();
  }

  public static String formatDate(long timeInMilliseconds) {
    LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(String.valueOf(timeInMilliseconds))),
                                                     TimeZone.getDefault().toZoneId());
    return dateTime.format(DATE_FORMATTER);
  }

  /**
   * The attributes stamped on an event for its space, the only ones ever
   * written: a page value sent under one of these names is dropped on the
   * websocket path, and the typed writer of each keeps its Elasticsearch
   * mapping (a long for a count or a time, never a keyword).
   */
  public static final List<SpaceStatisticAttribute> SPACE_ATTRIBUTES = List.of(
      new SpaceStatisticAttribute("parentSpaceId", StatisticParameterType.KEYWORD, Space::getParentSpaceId),
      new SpaceStatisticAttribute("spaceTemplateId", StatisticParameterType.KEYWORD, Space::getTemplateId),
      new SpaceStatisticAttribute("spaceCategoryIds", StatisticParameterType.KEYWORD, Space::getCategoryIds),
      new SpaceStatisticAttribute("spaceVisibility", StatisticParameterType.KEYWORD, Space::getVisibility),
      new SpaceStatisticAttribute("spaceRegistration", StatisticParameterType.KEYWORD, Space::getRegistration),
      new SpaceStatisticAttribute("spaceCreatedTime", StatisticParameterType.LONG, Space::getCreatedTime),
      new SpaceStatisticAttribute("spaceMembersCount", StatisticParameterType.LONG, space -> getSize(space.getMembers())),
      new SpaceStatisticAttribute("spaceManagersCount", StatisticParameterType.LONG, space -> getSize(space.getManagers())),
      new SpaceStatisticAttribute("spaceRedactorsCount", StatisticParameterType.LONG, space -> getSize(space.getRedactors())),
      new SpaceStatisticAttribute("spaceInviteesCount", StatisticParameterType.LONG, space -> getSize(space.getInvitedUsers())),
      new SpaceStatisticAttribute("spacePendingCount", StatisticParameterType.LONG, space -> getSize(space.getPendingUsers())),
      new SpaceStatisticAttribute("spaceSovereign", StatisticParameterType.BOOLEAN, Space::isSovereign));

  /** The names of {@link #SPACE_ATTRIBUTES}. */
  public static final Set<String> SPACE_ATTRIBUTE_NAMES = SPACE_ATTRIBUTES.stream()
                                                                          .map(SpaceStatisticAttribute::name)
                                                                          .collect(Collectors.toUnmodifiableSet());

  public static void addSpaceStatistics(StatisticData statisticData, Space space) {
    if (space == null || StringUtils.isBlank(space.getId())) {
      return;
    }
    statisticData.setSpaceId(Long.parseLong(space.getId()));
    SPACE_ATTRIBUTES.forEach(attribute -> attribute.write(statisticData, space));
  }

  public static void addActivityStatisticsData(StatisticData statisticData, ExoSocialActivity activity) {
    if (activity == null || StringUtils.isBlank(activity.getId())) {
      return;
    }
    String activityId = activity.getParentId() == null ? activity.getId() : activity.getParentId();
    String commentId = activity.getParentCommentId() == null ? activity.getId() : activity.getParentCommentId();
    String subCommentId = activity.getParentCommentId() == null ? null : activity.getId();
    statisticData.addKeyword(FIELD_ACTIVITY_TYPE, getActivityType(activity));
    if (StringUtils.isNotBlank(activityId)) {
      statisticData.addKeyword("activityId", activityId);
    }
    if (StringUtils.isNotBlank(commentId)) {
      commentId = commentId.replace(ACTIVITY_COMMENT, "");
      statisticData.addKeyword(ACTIVITY_COMMENT, commentId);
    }
    if (StringUtils.isNotBlank(subCommentId)) {
      subCommentId = subCommentId.replace(ACTIVITY_COMMENT, "");
      statisticData.addKeyword("subCommentId", subCommentId);
    }
  }

  public static void addCategoryStatistics(StatisticData statisticData, long categoryId) {
    CategoryService categoryService = ExoContainerContext.getService(CategoryService.class);
    addCategoryStatistics(statisticData, categoryService.getCategory(categoryId));
  }

  public static void addCategoryStatistics(StatisticData statisticData, Category category) {
    if (category == null) {
      return;
    }
    statisticData.addKeyword("categoryId", category.getId());
    statisticData.addKeyword("categoryIcon", category.getIcon());
    statisticData.addKeyword("categoryLinkPermissions", category.getLinkPermissions());
    statisticData.addKeyword("categoryCreatorId", category.getCreatorId());
    statisticData.addKeyword("categoryOwnerId", category.getOwnerId());
    statisticData.addKeyword("categoryParentId", category.getParentId());
  }

  public static void addCategoryLinkStatistics(StatisticData statisticData, CategoryObject categoryObject) {
    statisticData.addKeyword("categoryObjectType", categoryObject.getType());
    statisticData.addKeyword("categoryObjectId", categoryObject.getId());
    statisticData.addKeyword("categoryObjectParentId", categoryObject.getParentId());
    if (categoryObject.getSpaceId() > 0) {
      SpaceService spaceService = CommonsUtils.getService(SpaceService.class);
      Space space = spaceService.getSpaceById(categoryObject.getSpaceId());
      if (space != null) {
        addSpaceStatistics(statisticData, space);
      }
    }
  }

  public static void convertFieldName(Supplier<String> supplier,
                                      Consumer<String> consumer,
                                      Set<StatisticFieldMapping> mappings,
                                      boolean isAggregation) {
    String fieldName = supplier.get();
    if (StringUtils.isBlank(fieldName) || mappings == null || mappings.isEmpty()) {
      return;
    }
    String fieldNameNoKeyword = fieldName.replace(KEYWORD_FIELD_NAME_SUFFIX, "");
    for (int i = MAX_ALTERNATIVE_FIELD_COUNT; i >= 1; i--) {
      String altFieldName = getAlternativeFieldName(fieldNameNoKeyword, i);
      // Check if there is an alternative field mapping
      if (convertKeywordFieldName(consumer, altFieldName, mappings, isAggregation, true)) {
        return;
      }
    }
    // Finally, if no alternative mapping, apply the modification on the
    // principal field Mapping instead of alternative
    convertKeywordFieldName(consumer, fieldName, mappings, isAggregation, false);
  }

  public static String getAlternativeFieldName(String fieldName, int alternativeIndex) {
    String baseFieldName = getBaseFieldName(fieldName);
    return alternativeIndex == 1 ? baseFieldName + ALTERNATIVE_FIELD_SUFFIX :
                                 baseFieldName + ALTERNATIVE_FIELD_SUFFIX + alternativeIndex;
  }

  public static String getBaseFieldName(String fieldName) {
    return fieldName == null ? null : fieldName.replaceFirst(ALTERNATIVE_FIELD_SUFFIX + "\\d*$", "");
  }

  private static boolean convertKeywordFieldName(Consumer<String> consumer,
                                                 String fieldName,
                                                 Set<StatisticFieldMapping> mappings,
                                                 boolean isAggregation,
                                                 boolean skipTypeConflict) {
    String fieldNameNoKeyword = fieldName.replace(KEYWORD_FIELD_NAME_SUFFIX, "");
    StatisticFieldMapping mapping = mappings.stream()
                                            .filter(f -> f.getName().equals(fieldNameNoKeyword))
                                            .findFirst()
                                            .orElse(null);
    if (mapping == null) {
      return false;
    }
    boolean keywordSubFieldAggregation = isAggregation
                                         && mapping.isHasKeywordSubField()
                                         && StringUtils.equals(mapping.getType(), "text");
    if (skipTypeConflict && mapping.isTypeConflict() && !keywordSubFieldAggregation) {
      return false;
    }
    if (keywordSubFieldAggregation) {
      consumer.accept(fieldNameNoKeyword + KEYWORD_FIELD_NAME_SUFFIX);
    } else {
      consumer.accept(fieldNameNoKeyword);
    }
    return true;
  }

  private static int getSize(String[] array) {
    return array == null ? 0 : (int) Arrays.stream(array).filter(Objects::nonNull).distinct().count();
  }

  private static String getActivityType(ExoSocialActivity activity) {
    String type = activity.getType();
    if (type == null || type.isEmpty()) {
      if (activity.getFiles() != null && !activity.getFiles().isEmpty()) {
        type = FILE_SPACES_ACTIVITY_TYPE;
      } else if ((activity.getFiles() == null || activity.getFiles().isEmpty()) && activity.getTemplateParams() != null
                 && !activity.getTemplateParams().isEmpty()
                 && activity.getTemplateParams().get("link") != null
                 && !activity.getTemplateParams().get("link").equals("-")) {
        type = LINK_ACTIVITY_TYPE;
      }
    }
    return type;
  }

  private static long parseId(String id) {
    try {
      return Long.parseLong(id);
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  private static void addUserStatistics(StatisticData statisticData) {
    if (statisticData.getUserId() == 0) {
      return;
    }
    Identity identity = getIdentity(String.valueOf(statisticData.getUserId()));
    if (identity != null && identity.getProfile() != null) {
      Map<String, Object> properties = identity.getProfile().getProperties();
      List<ProfilePropertySetting> propertySettings = getProfilePropertyService().getPropertySettings();
      propertySettings.stream()
                      .filter(ProfilePropertySetting::isIndexInAnalytics)
                      .forEach(property -> {
                        String propertyName = property.getPropertyName();
                        Object propertyValue = properties.get(propertyName);
                        if (properties.containsKey(propertyName)) {
                          if (propertyValue instanceof String value) {
                            statisticData.addKeyword(String.join(".", PROFILE_PROPERTIES, propertyName), value);
                          } else if (!property.isHasChildProperties()
                                     && propertyValue instanceof List<?> values) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, String>> multiValues = (List<Map<String, String>>) values;
                            List<String> valuesList = multiValues.stream()
                                                                 .map(prop -> prop.get("value"))
                                                                 .filter(Objects::nonNull)
                                                                 .toList();
                            statisticData.addKeyword(String.join(".", PROFILE_PROPERTIES, propertyName), valuesList);
                          }
                        }
                      });
    }
  }

  private static void mergeObjectNodes(ObjectNode target, ObjectNode source) { // NOSONAR
    for (Iterator<Map.Entry<String, JsonNode>> it = source.properties().iterator(); it.hasNext();) {
      Map.Entry<String, JsonNode> entry = it.next();
      String key = entry.getKey();
      JsonNode newValue = entry.getValue();
      if (!target.has(key)) {
        target.set(key, newValue);
        continue;
      }
      JsonNode existingValue = target.get(key);
      if (existingValue.isObject() && newValue.isObject()) {
        ObjectNode existingObj = (ObjectNode) existingValue;
        ObjectNode newObj = (ObjectNode) newValue;
        JsonNode existingProps = existingObj.get(PROPERTIES);
        JsonNode newProps = newObj.get(PROPERTIES);
        if (existingProps != null && newProps != null && existingProps.isObject() && newProps.isObject()) {
          // Always descend: an object present in both indices may hold a
          // sub-field whose type changed between them (EXO-90504). Descending
          // only when the newer index brought a new sub-field kept the older
          // index's types for every sub-field the two had in common.
          ObjectNode existingPropsObj = (ObjectNode) existingProps;
          mergeObjectNodes(existingPropsObj, (ObjectNode) newProps);
          existingObj.set(PROPERTIES, existingPropsObj);
          target.set(key, existingObj);
        } else {
          target.set(key, newValue);
        }
      } else {
        target.set(key, newValue);
      }
    }
  }

  /**
   * @deprecated since 7.3.0, for removal: assumes the default index prefix,
   *             which made the mapping read return nothing on a deployment
   *             with a custom {@code analytics.es.index.prefix} (EXO-90504).
   *             Use {@link #sortByAnalyticsDate(JSONObject, String)} with the
   *             configured prefix.
   */
  @Deprecated(since = "7.3.0", forRemoval = true)
  public static ObjectNode sortByAnalyticsDate(JSONObject input) {
    return sortByAnalyticsDate(input, DEFAULT_INDEX_PREFIX);
  }

  /**
   * Sorts the indices of an Elasticsearch {@code _mapping} response by the
   * date carried in their name, oldest first.
   *
   * @param input the {@code _mapping} response, keyed by index name
   * @param indexPrefix the configured {@code analytics.es.index.prefix}; an
   *          index whose name is not {@code <prefix>_yyyy-MM-dd} is left out
   * @return the same entries, in ascending date order
   */
  public static ObjectNode sortByAnalyticsDate(JSONObject input, String indexPrefix) {
    Map<LocalDate, String> dateKeyMap = new TreeMap<>();
    String prefix = StringUtils.defaultIfBlank(indexPrefix, DEFAULT_INDEX_PREFIX);
    Pattern pattern = Pattern.compile(Pattern.quote(prefix) + "_(\\d{4}-\\d{2}-\\d{2})");

    for (String key : input.keySet()) {
      Matcher matcher = pattern.matcher(key);
      if (matcher.matches()) {
        LocalDate date = LocalDate.parse(matcher.group(1));
        dateKeyMap.put(date, key);
      }
    }
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode sortedNode = mapper.createObjectNode();

    for (Map.Entry<LocalDate, String> entry : dateKeyMap.entrySet()) {
      String key = entry.getValue();
      Object raw = input.get(key);
      try {
        JsonNode valueNode = mapper.readTree(raw.toString());
        sortedNode.set(key, valueNode);
      } catch (Exception e) {
        sortedNode.putPOJO(key, raw);
      }
    }
    return sortedNode;
  }

  private static ProfilePropertyService getProfilePropertyService() {
    if (profilePropertyService == null) {
      profilePropertyService = CommonsUtils.getService(ProfilePropertyService.class);
    }
    return profilePropertyService;
  }
}
