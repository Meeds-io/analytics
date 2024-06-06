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
package io.meeds.analytics.listener.portal;

import static io.meeds.analytics.utils.AnalyticsUtils.addStatisticData;

import java.util.Map;

import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;

import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.utils.AnalyticsUtils;

@Asynchronous
public class LoginFailedAnalyticsListener extends Listener<String, Map<String, String>> {

  @Override
  @ExoTransactional
  public void onEvent(Event<String, Map<String, String>> event) throws Exception {
    Map<String, String> data = event.getData();

    StatisticData statisticData = new StatisticData();
    statisticData.setModule("portal");
    statisticData.setSubModule("login");
    statisticData.setOperation("login");
    statisticData.setUserId(AnalyticsUtils.getUserIdentityId(data.get("user_id")));
    statisticData.setStatus(data.get("status").equals("ko") ? StatisticData.StatisticStatus.KO
                                                            : StatisticData.StatisticStatus.OK);
    statisticData.addParameter("reason", data.get("reason"));
    addStatisticData(statisticData);
  }
}
