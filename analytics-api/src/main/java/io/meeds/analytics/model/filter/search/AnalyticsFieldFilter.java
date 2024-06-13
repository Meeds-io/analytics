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
package io.meeds.analytics.model.filter.search;

import java.io.Serializable;

import io.meeds.analytics.model.filter.AnalyticsFilter.Range;

import groovy.transform.ToString;
import lombok.*;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsFieldFilter implements Serializable, Cloneable {

  private static final long        serialVersionUID = 5480543226777844698L;

  private String                   field;

  private AnalyticsFieldFilterType type;

  private String                   valueString;

  private Range                    range;

  public AnalyticsFieldFilter(String field, AnalyticsFieldFilterType type, String valueString) {
    this.field = field;
    this.type = type;
    this.valueString = valueString;
  }

  public AnalyticsFieldFilter(String field, AnalyticsFieldFilterType type, Range range) {
    this.field = field;
    this.type = type;
    this.range = range;
  }

  public AnalyticsFieldFilter(String field, AnalyticsFieldFilterType type) {
    this.field = field;
    this.type = type;
  }

  @Override
  public AnalyticsFieldFilter clone() { // NOSONAR
    return new AnalyticsFieldFilter(field, type, valueString, range);
  }
}
