package org.exoplatform.analytics.listener.social;

import org.apache.commons.lang.Validate;
import org.exoplatform.analytics.model.StatisticData;
import org.exoplatform.analytics.utils.AnalyticsUtils;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.processor.MetadataActivityProcessor;
import org.exoplatform.social.metadata.tag.model.TagName;

import java.util.Date;
import java.util.Set;

@Asynchronous
public class AnalyticsTagsListener extends Listener<String, Set<TagName>> {

    private ActivityManager activityManager;

    private IdentityManager identityManager;

    public AnalyticsTagsListener(ActivityManager activityManager, IdentityManager identityManager) {
        this.activityManager = activityManager;
        this.identityManager = identityManager;
    }

    @Override
    public void onEvent(Event<String, Set<TagName>> event) throws Exception {
        Set<TagName> tagNames = event.getData();
        String activityId = event.getSource();
        addEventStatistic(tagNames, activityId);
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
