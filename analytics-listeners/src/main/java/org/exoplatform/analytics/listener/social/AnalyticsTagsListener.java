package org.exoplatform.analytics.listener.social;

import org.apache.commons.lang.Validate;
import org.exoplatform.analytics.listener.kudos.KudosSentListener;
import org.exoplatform.analytics.model.StatisticData;
import org.exoplatform.analytics.utils.AnalyticsUtils;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.ActivityManagerImpl;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.processor.MetadataActivityProcessor;
import org.exoplatform.social.metadata.tag.model.TagName;

import java.util.Date;
import java.util.Set;

@Asynchronous
public class AnalyticsTagsListener extends Listener<ExoSocialActivity, Set<TagName>> {

    private ActivityManager activityManager;

    private IdentityManager identityManager;

    private PortalContainer container;

    public AnalyticsTagsListener(ActivityManager activityManager, IdentityManager identityManager) {
        this.activityManager = activityManager;
        this.identityManager = identityManager;
        this.container = PortalContainer.getInstance();
    }

    @Override
    public void onEvent(Event<ExoSocialActivity, Set<TagName>> event) throws Exception {
        Set<TagName> tagNames = event.getData();
        ExoSocialActivity activity = event.getSource();
        addEventStatistic(tagNames, activity);
    }

    private void addEventStatistic(Set<TagName> tagNames, ExoSocialActivity activity) {

        int numberOfTags = tagNames.size();
        String objectType = MetadataActivityProcessor.ACTIVITY_METADATA_OBJECT_TYPE;
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
