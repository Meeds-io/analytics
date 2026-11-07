/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
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
package io.meeds.analytics.constant;

import io.meeds.analytics.model.StatisticData;

/**
 * The typed writers of {@link StatisticData}, so that a parameter written by
 * name from a table gets the Elasticsearch mapping of its type: a keyword for
 * an id or an enumeration, a long for a count or a timestamp, a boolean.
 */
public enum StatisticParameterType {

  KEYWORD,
  LONG,
  BOOLEAN;

  /**
   * Writes the value on the statistic data with the writer of this type.
   *
   * @param statisticData the event
   * @param key the parameter name
   * @param value the value, ignored when null
   */
  public void write(StatisticData statisticData, String key, Object value) {
    switch (this) {
    case KEYWORD -> statisticData.addKeyword(key, value);
    case LONG -> statisticData.addLong(key, value);
    case BOOLEAN -> statisticData.addBoolean(key, value);
    }
  }

}
