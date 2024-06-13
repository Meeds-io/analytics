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
package io.meeds.analytics.model;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

@Data
public class AnalyticsWebSocketMessage {

  /**
   * Watcher name from {@link StatisticWatcher#getName()}
   */
  private String              name;

  /**
   * statistic module
   */
  private String              module;

  /**
   * statistic subModule
   */
  private String              subModule;

  /**
   * statistic operation
   */
  private String              operation;

  /**
   * User login name
   */
  private String              userName;

  /**
   * Current technical space pretty name
   */
  private String              spacePrettyName;

  /**
   * Current technical space id
   */
  private String              spaceId;

  /**
   * Current portalUri
   */
  private String              portalUri;

  /**
   * User cometd Token
   */
  private String              token;

  /**
   * Collected DOM properties switch {@link StatisticWatcher#getDomProperties()}
   * and DOM event properties collected using
   * {@link StatisticWatcher#getDomEventProperties()}
   */
  private Map<String, String> parameters = new HashMap<>();

}
