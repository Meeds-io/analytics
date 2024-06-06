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
package io.meeds.analytics.api.service;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class StatisticWatcher {

  /**
   * Statistic data: name field
   */
  private String              name;

  /**
   * Statistic data: module field
   */
  private String              module;

  /**
   * Statistic data: subModule field
   */
  private String              subModule;

  /**
   * Statistic data: operation field
   */
  private String              operation;

  /**
   * Statistic data: additional embedded parameters
   */
  private Map<String, String> parameters;

  /**
   * DOM jquery selector, used to search elements
   */
  private String              domSelector;

  /**
   * DOM jquery event to listen, used to trigger storing new statistic data
   */
  private String              domEvent;

  /**
   * DOM jquery element attributes ('attr' method) or data ('data' method) to
   * collect and store in statistic data
   */
  private List<String>        domProperties;

  /**
   * DOM triggered event attributes to collect and store in statistic data
   */
  private List<String>        domEventProperties;

}
