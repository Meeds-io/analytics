/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io
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
package io.meeds.analytics.api.service.injection;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.meeds.analytics.api.service.AnalyticsService;
import io.meeds.analytics.model.StatisticWatcher;
import io.meeds.analytics.model.StatisticWatchersDescriptor;
import io.meeds.social.util.JsonUtils;

import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;

@Component
public class StatisticWatcherImportService {

  @Autowired
  private AnalyticsService analyticsService;

  @PostConstruct
  public void init() {
    CompletableFuture.runAsync(this::importUIWatchers);
  }

  @SneakyThrows
  public void importUIWatchers() {
    Collections.list(getClass().getClassLoader().getResources("analytics-ui-watchers.json"))
               .stream()
               .map(this::parseDescriptors)
               .flatMap(List::stream)
               .forEach(analyticsService::addUIWatcher);
  }

  @SneakyThrows
  protected List<StatisticWatcher> parseDescriptors(URL url) {
    try (InputStream inputStream = url.openStream()) {
      String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
      StatisticWatchersDescriptor list = JsonUtils.fromJsonString(content, StatisticWatchersDescriptor.class);
      return list.getDescriptors();
    }
  }

}
