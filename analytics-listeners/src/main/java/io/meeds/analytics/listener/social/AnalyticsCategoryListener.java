/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package io.meeds.analytics.listener.social;

import static io.meeds.social.category.service.CategoryService.EVENT_SOCIAL_CATEGORY_CREATED;
import static io.meeds.social.category.service.CategoryService.EVENT_SOCIAL_CATEGORY_DELETED;
import static io.meeds.social.category.service.CategoryService.EVENT_SOCIAL_CATEGORY_UPDATED;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.listener.ListenerService;

import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.utils.AnalyticsUtils;
import io.meeds.social.category.model.Category;

import jakarta.annotation.PostConstruct;

@Component
public class AnalyticsCategoryListener extends Listener<Category, Long> {

  @Autowired
  private ListenerService listenerService;

  @PostConstruct
  public void init() {
    listenerService.addListener(EVENT_SOCIAL_CATEGORY_CREATED, this);
    listenerService.addListener(EVENT_SOCIAL_CATEGORY_UPDATED, this);
    listenerService.addListener(EVENT_SOCIAL_CATEGORY_DELETED, this);
  }

  @Override
  public void onEvent(Event<Category, Long> event) throws Exception {
    StatisticData statisticData = new StatisticData();
    statisticData.setTimestamp(new Date().getTime());
    statisticData.setModule("social");
    statisticData.setSubModule("category");
    switch (event.getEventName()) {
    case EVENT_SOCIAL_CATEGORY_CREATED:
      statisticData.setOperation("categoryCreated");
      break;
    case EVENT_SOCIAL_CATEGORY_UPDATED:
      statisticData.setOperation("categoryUpdated");
      break;
    case EVENT_SOCIAL_CATEGORY_DELETED:
      statisticData.setOperation("categoryDeleted");
      break;
    default:
      break;
    }
    AnalyticsUtils.addCategoryStatistics(statisticData, event.getSource());
    AnalyticsUtils.addStatisticData(statisticData);
  }

}
