/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2026 Meeds Association contact@meeds.io
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

import static io.meeds.analytics.utils.AnalyticsUtils.addStatisticData;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.listener.ListenerService;

import io.meeds.analytics.model.StatisticData;
import io.meeds.social.security.service.AccountDeactivationService;

import jakarta.annotation.PostConstruct;

/**
 * Tracks an analytics operation each time a user confirms the deactivation
 * request of their own account (event source = username, data = social
 * identity id), so that platform administrators can follow these requests in
 * reports.
 */
@Asynchronous
@Component
public class AccountDeactivationAnalyticsListener extends Listener<String, String> {

  public static final String OPERATION = "accountDeactivationRequest";

  @Autowired
  private ListenerService    listenerService;

  @PostConstruct
  public void init() {
    listenerService.addListener(AccountDeactivationService.ACCOUNT_DEACTIVATION_REQUESTED_EVENT, this);
  }

  @Override
  public void onEvent(Event<String, String> event) throws Exception {
    StatisticData statisticData = new StatisticData();
    statisticData.setModule("social");
    statisticData.setSubModule("account");
    statisticData.setOperation(OPERATION);
    statisticData.setUserId(Long.parseLong(event.getData()));
    addStatisticData(statisticData);
  }

}
