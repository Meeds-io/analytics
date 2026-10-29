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
package io.meeds.analytics.model;

import java.util.function.Function;

import org.exoplatform.social.core.space.model.Space;

import io.meeds.analytics.constant.StatisticParameterType;

/**
 * One attribute the server stamps on an event for its space: the parameter
 * name, the writer of its type and the value read from the space. The table
 * of these is the only source of the space attributes an event carries, so a
 * value a page sends under one of these names is never stored.
 *
 * @param name the parameter name
 * @param type the typed writer the attribute is written with
 * @param reader the value read from the space
 */
public record SpaceStatisticAttribute(String name, StatisticParameterType type, Function<Space, Object> reader) {

  /**
   * Writes the attribute of the given space on the event.
   *
   * @param statisticData the event
   * @param space the space the event belongs to
   */
  public void write(StatisticData statisticData, Space space) {
    type.write(statisticData, name, reader.apply(space));
  }

}
