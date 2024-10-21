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

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import lombok.*;
import lombok.EqualsAndHashCode.Exclude;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StatisticFieldMapping {

  private static final List<String> NUMERIC_TYPES = Arrays.asList("long", "double", "float", "short", "int");

  private String                    name;

  @Exclude
  private String                    type;

  @Exclude
  private boolean                   hasKeywordSubField;

  @Exclude
  private boolean                   scriptedField;

  public StatisticFieldMapping(String name, String type, boolean hasKeywordSubField) {
    this.name = name;
    this.type = type;
    this.hasKeywordSubField = hasKeywordSubField;
  }

  public static final String computeESQueryValue(String value) {
    if (NumberUtils.isDigits(value)) {
      return value;
    } else {
      return "\"" + value + "\"";
    }
  }

  public boolean isNumeric() {
    return NUMERIC_TYPES.contains(type);
  }

  public boolean isKeyword() {
    return StringUtils.equals(type, "keyword") || (hasKeywordSubField && StringUtils.equals(type, "text"));
  }

  public boolean isDate() {
    return StringUtils.equals(type, "date");
  }

  public boolean isText() {
    return StringUtils.equals(type, "text");
  }

  public boolean isAggregation() {
    return isNumeric() || isDate() || isKeyword();
  }

  public String getSearchFieldName() {
    return name;
  }

  public String getAggregationFieldName() {
    if (isAggregation() && hasKeywordSubField && StringUtils.equals(type, "text")) {
      return name + ".keyword";
    }
    return name;
  }

  public String getESQueryValue(String value) {
    if (isNumeric()) {
      return value;
    } else {
      return "\"" + value + "\"";
    }
  }

}
