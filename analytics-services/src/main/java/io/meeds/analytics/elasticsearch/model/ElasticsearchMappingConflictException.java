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
package io.meeds.analytics.elasticsearch.model;

/**
 * Raised when Elasticsearch refuses a bulk because a document value does not
 * match the type the write index holds for its field. It means the mapping
 * view the documents were built against is stale: the write index acquired a
 * type the view does not know yet, typically right after a weekly rollover
 * (EXO-90504). The caller refreshes the view and retries once.
 */
public class ElasticsearchMappingConflictException extends IllegalStateException {

  private static final long serialVersionUID = -2860712344563196152L;

  public ElasticsearchMappingConflictException(String message) {
    super(message);
  }

}
