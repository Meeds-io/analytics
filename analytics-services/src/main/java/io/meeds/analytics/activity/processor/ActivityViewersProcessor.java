package io.meeds.analytics.activity.processor;

import io.meeds.analytics.api.service.AnalyticsService;
import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.filter.AnalyticsFilter;
import jakarta.annotation.PostConstruct;
import org.apache.commons.collections4.CollectionUtils;
import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.BaseActivityProcessorPlugin;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.MetadataItem;
import org.exoplatform.social.metadata.model.MetadataKey;
import org.exoplatform.social.metadata.model.MetadataType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ActivityViewersProcessor extends BaseActivityProcessorPlugin {

  private static final Log         LOG                     = ExoLogger.getLogger(ActivityViewersProcessor.class);

  private static final String      ACTIVITY_PROCESSOR_NAME = "ActivityViewersProcessor";

  private static final String      METADATA_NAME           = "viewers";

  public static final MetadataType METADATA_TYPE           = new MetadataType(METADATA_NAME.hashCode(), METADATA_NAME);

  @Autowired
  private MetadataService          metadataService;

  @Autowired
  private ActivityManager          activityManager;

  @Autowired
  private AnalyticsService         analyticsService;

  public ActivityViewersProcessor() {
        super(initParams());
    }

  @Override
  public String getName() {
        return ACTIVITY_PROCESSOR_NAME;
    }

  @PostConstruct
  public void init() {
        activityManager.addProcessor(this);
    }

  @Override
  public void processActivity(ExoSocialActivity activity) {

    String authorId = activity.getUserId();
    MetadataKey metadataKey = new MetadataKey(METADATA_TYPE.getName(), METADATA_NAME, Long.parseLong(authorId));
    List<MetadataItem> viewersIdentityIds = metadataService.getMetadataItemsByMetadataAndObject(metadataKey, activity.getMetadataObject());
    if (CollectionUtils.isEmpty(viewersIdentityIds)) {
      AnalyticsFilter filter = new AnalyticsFilter();
      filter.addEqualFilter("operation", "markAsRead");
      filter.addEqualFilter("entityType", "activity");
      filter.addEqualFilter("entityId", activity.getId());
      filter.addEqualFilter("event-type", "read");
      List<StatisticData> stats = analyticsService.retrieveData(filter);
      for (StatisticData stat : stats) {
        long viewerId = stat.getUserId();
        if (String.valueOf(viewerId).equals(authorId)) {
          continue;
        }
        try {
          metadataService.createMetadataItem(activity.getMetadataObject(), metadataKey, viewerId);
        } catch (ObjectAlreadyExistsException e) {
          LOG.warn("Metadata already exists for viewer {} on activity {}", viewerId, activity.getId(), e);
        }
      }
    }
  }

  private static InitParams initParams() {
    InitParams initParams = new InitParams();
    ValueParam param = new ValueParam();
    param.setName("priority");
    param.setValue("20");
    initParams.addParameter(param);
    return initParams;
  }
}
