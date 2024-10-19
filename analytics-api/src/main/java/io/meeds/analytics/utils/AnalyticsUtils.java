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
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.social.core.activity.model.ActivityStream;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.ws.frameworks.json.impl.JsonDefaultHandler;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.exoplatform.ws.frameworks.json.impl.JsonGeneratorImpl;
import org.exoplatform.ws.frameworks.json.impl.JsonParserImpl;
import org.exoplatform.ws.frameworks.json.impl.ObjectBuilder;

import io.meeds.analytics.api.service.StatisticDataQueueService;
import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.StatisticData.StatisticStatus;

public class AnalyticsUtils {
  private static final Log              LOG                              = ExoLogger.getLogger(AnalyticsUtils.class);

  public static final short             MAX_BULK_DOCUMENTS               = 100;

  public static final String            VALUES_SEPARATOR                 = ",";

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

  public static final String            FIELD_MODIFIER_USER_SOCIAL_ID    = "modifierSocialId";

  public static final String            FIELD_SOCIAL_IDENTITY_ID         = "identityId";

  public static final String            LINK_ACTIVITY_TYPE               = "LINK_ACTIVITY";

  public static final String            FILE_SPACES_ACTIVITY_TYPE        = "files:spaces";

  public static final String            ACTIVITY_COMMENT                 = "comment";

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
    String defaultLabel = (chartKey == null ? "" : chartKey.replace(".keyword", "") + "=") + chartValue;
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
        Identity identity = identityManager.getIdentity(chartValue);
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

  public static final JSONObject getJSONObject(JSONObject jsonObject, int i, String... keys) { // NOSONAR
    if (keys == null || i >= keys.length) {
      return null;
    }
    try {
      if (keys[i] == null) {
        String[] names = JSONObject.getNames(jsonObject);
        if (ArrayUtils.isNotEmpty(names)) {
          i++;
          JSONObject resultJsonObject = new JSONObject();
          for (int j = 0; j < names.length; j++) {
            String name = names[j];
            JSONObject subJsonObject = jsonObject.getJSONObject(name);
            JSONObject resultSubJsonObject = getJSONObject(subJsonObject, i, keys);
            String[] attributes = JSONObject.getNames(resultSubJsonObject);
            for (int k = 0; k < attributes.length; k++) {
              String attribute = attributes[k];
              resultJsonObject.put(attribute, resultSubJsonObject.get(attribute));
            }
          }
          return resultJsonObject;
        } else {
          return null;
        }
      } else if (jsonObject.has(keys[i])) {
        jsonObject = jsonObject.getJSONObject(keys[i]);
        i++;
        if (i == keys.length) {
          return jsonObject;
        } else {
          return getJSONObject(jsonObject, i, keys);
        }
      } else {
        return null;
      }
    } catch (JSONException e) {
      LOG.warn("Error getting key object with {}", keys[i], e);
      return null;
    }
  }

  @ExoTransactional
  public static final void addStatisticData(StatisticData statisticData) {
    if (statisticData == null || statisticData.getOperation() == null) {
      return;
    }
    if (statisticData.getTimestamp() <= 0) {
      statisticData.setTimestamp(System.currentTimeMillis());
    }
    if (statisticData.getStatus() == null) {
      statisticData.setStatus(StatisticStatus.OK);
    }

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

  public static Identity getIdentity(String identityId) {
    if (StringUtils.isBlank(identityId)) {
      return null;
    }
    IdentityManager identityManager = CommonsUtils.getService(IdentityManager.class);
    return identityManager.getIdentity(identityId);
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

  public static void addSpaceStatistics(StatisticData statisticData, Space space) {
    if (space == null || StringUtils.isBlank(space.getId())) {
      return;
    }
    statisticData.setSpaceId(Long.parseLong(space.getId()));
    statisticData.addParameter("spaceVisibility", space.getVisibility());
    statisticData.addParameter("spaceRegistration", space.getRegistration());
    statisticData.addParameter("spaceCreatedTime", space.getCreatedTime());
    statisticData.addParameter("spaceMembersCount", getSize(space.getMembers()));
    statisticData.addParameter("spaceManagersCount", getSize(space.getManagers()));
    statisticData.addParameter("spaceRedactorsCount", getSize(space.getRedactors()));
    statisticData.addParameter("spaceInviteesCount", getSize(space.getInvitedUsers()));
    statisticData.addParameter("spacePendingCount", getSize(space.getPendingUsers()));
  }

  public static void addActivityStatisticsData(StatisticData statisticData, ExoSocialActivity activity) {
    if (activity == null || StringUtils.isBlank(activity.getId())) {
      return;
    }
    String activityId = activity.getParentId() == null ? activity.getId() : activity.getParentId();
    String commentId = activity.getParentCommentId() == null ? activity.getId() : activity.getParentCommentId();
    String subCommentId = activity.getParentCommentId() == null ? null : activity.getId();
    statisticData.addParameter("activityType", getActivityType(activity));
    if (StringUtils.isNotBlank(activityId)) {
      statisticData.addParameter("activityId", activityId);
    }
    if (StringUtils.isNotBlank(commentId)) {
      commentId = commentId.replace(ACTIVITY_COMMENT, "");
      statisticData.addParameter(ACTIVITY_COMMENT, commentId);
    }
    if (StringUtils.isNotBlank(subCommentId)) {
      subCommentId = subCommentId.replace(ACTIVITY_COMMENT, "");
      statisticData.addParameter("subCommentId", subCommentId);
    }
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
}
