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
package io.meeds.analytics.model.filter;

import java.io.Serializable;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsTableColumnFilter implements Serializable, Cloneable {

  private static final long               serialVersionUID = 1650453023117737022L;

  private String                          title;

  private String                          userField;

  private String                          spaceField;

  private AnalyticsTableColumnAggregation valueAggregation;

  private AnalyticsTableColumnAggregation thresholdAggregation;

  private String                          dataType;

  private boolean                         sortable;

  private boolean                         previousPeriod;

  private String                          width;

  private String                          align;

  @Override
  public AnalyticsTableColumnFilter clone() { // NOSONAR
    AnalyticsTableColumnAggregation clonedAggregation = valueAggregation == null ? null : valueAggregation.clone();
    AnalyticsTableColumnAggregation clonedThresholdAggregation =
                                                               thresholdAggregation == null ? null : thresholdAggregation.clone();
    return new AnalyticsTableColumnFilter(title,
                                          userField,
                                          spaceField,
                                          clonedAggregation,
                                          clonedThresholdAggregation,
                                          dataType,
                                          sortable,
                                          previousPeriod,
                                          width,
                                          align);
  }
}
