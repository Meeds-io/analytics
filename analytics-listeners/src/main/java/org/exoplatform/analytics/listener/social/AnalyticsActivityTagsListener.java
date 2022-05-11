package org.exoplatform.analytics.listener.social;

import java.util.Date;
import java.util.Set;

import org.apache.commons.lang.Validate;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.analytics.model.StatisticData;
import org.exoplatform.analytics.utils.AnalyticsUtils;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.activity.model.ExoSocialActivityImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.metadata.tag.model.TagName;
import org.exoplatform.social.metadata.tag.model.TagObject;

@Asynchronous
public class AnalyticsActivityTagsListener extends Listener<TagObject, Set<TagName>> {

  private ActivityManager activityManager;

  private IdentityManager identityManager;

  public AnalyticsActivityTagsListener(ActivityManager activityManager, IdentityManager identityManager) {
    this.activityManager = activityManager;
    this.identityManager = identityManager;
  }

  @Override
  public void onEvent(Event<TagObject, Set<TagName>> event) throws Exception {
    TagObject tagObject = event.getSource();
    if (StringUtils.equals(tagObject.getType(), ExoSocialActivityImpl.DEFAULT_ACTIVITY_METADATA_OBJECT_TYPE)) {
      addEventStatistic(event.getData(), tagObject.getId());
    }
  }

  private void addEventStatistic(Set<TagName> tagNames, String activityId) {
    int numberOfTags = tagNames.size();
    String objectType;
    ExoSocialActivity activity = activityManager.getActivity(activityId);
    if (activity.getType() == null) {
      objectType = activity.isComment() ? "COMMENT" : "ACTIVITY";
    } else {
      objectType = activity.getType();
    }
    Identity audienceIdentity = activityManager.getActivityStreamOwnerIdentity(activity.getId());
    String username = getActivityPoster(activity);
    StatisticData statisticData = new StatisticData();
    statisticData.setModule("portal");
    statisticData.setSubModule("ui");
    statisticData.setOperation("Add tag");
    statisticData.setTimestamp(new Date().getTime());
    statisticData.setUserId(Long.parseLong(activity.getUserId()));
    statisticData.addParameter("username", username);
    statisticData.addParameter("type", objectType);
    statisticData.addParameter("spaceId", audienceIdentity.getId());

    for (int i = 0; i < numberOfTags; i++) {
      AnalyticsUtils.addStatisticData(statisticData);
    }
  }

  private String getActivityPoster(ExoSocialActivity activity) {
    Validate.notNull(activity.getUserId(), "activity.getUserId() must not be null!");
    return identityManager.getIdentity(activity.getUserId()).getRemoteId();
  }
}
