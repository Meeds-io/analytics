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
package io.meeds.analytics.rest;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.meeds.analytics.api.service.AnalyticsService;
import io.meeds.analytics.model.StatisticData;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("samples")
@Tag(name = "samples", description = "Retrieve Analytics Samples")
public class AnalyticsSampleRest {

  @Autowired
  private AnalyticsService analyticsService;

  @GetMapping
  @Secured("analytics")
  @Operation(summary = "Retrieve Analytics Samples",
             method = "GET",
             description = "This will allow for an administrator to retrieve collected statistics without user ACL retrictions")
  @ApiResponses(value = {
                          @ApiResponse(responseCode = "200", description = "Request fulfilled"),
  })
  public List<StatisticData> getSamples(
                                        @Parameter(description = "Collected Statistic Operations to consider in query",
                                                   required = false)
                                        @RequestParam(name = "operation",
                                                      required = false)
                                        List<String> operations,
                                        @Parameter(description = "Filtered field name",
                                                   required = false)
                                        @RequestParam(name = "fieldName",
                                                      required = false)
                                        String fieldName,
                                        @Parameter(description = "Filtered field values",
                                                   required = false)
                                        @RequestParam(name = "fieldValue",
                                                      required = false)
                                        List<String> fieldValues,
                                        @Parameter(description = "Sort field name",
                                                   required = true)
                                        @RequestParam("sortBy")
                                        String sortBy,
                                        @Parameter(description = "Sort direction: asc or desc",
                                                   required = true)
                                        @RequestParam("sortDirection")
                                        String sortDirection,
                                        @Parameter(description = "Limit of samples to retrieve",
                                                   required = true)
                                        @RequestParam("limit")
                                        int limit) {
    return analyticsService.getSamples(operations,
                                       fieldName,
                                       fieldValues,
                                       sortBy,
                                       sortDirection,
                                       limit);
  }

}
