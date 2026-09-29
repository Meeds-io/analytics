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
package io.meeds.analytics.elasticsearch;

import java.time.Duration;

import org.apache.commons.lang3.StringUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Provides the Elasticsearch the {@code *IT} classes execute their
 * hand-written requests against: the weekly index creation, the alias
 * write switch, the explicit and dynamic field mappings, the bulk and its
 * rejections. These are exactly what a mocked {@code HttpClient} cannot
 * check ({@code backend-spring.md} §7), and the mapping-conflict incident
 * this harness was written for (EXO-90504) was invisible to every unit
 * test of the module.
 * <p>
 * Off in a plain build: the {@code *IT} classes run under the parent's
 * {@code run-its} profile only ({@code mvn verify -Prun-its}). One container
 * is started for the whole fork and left to Ryuk to reap. Setting
 * {@code -Des.url} points the suite at an already-running engine instead.
 * <b>The suite uses its own index prefix, alias and template name</b>
 * ({@code it_analytics*}), so an engine holding a platform's
 * {@code analytics_*} indices can be used safely; it never touches them.
 * <p>
 * Same shape as {@code ai-rag}'s harness, the first module in the org to
 * need a containerised engine.
 */
public abstract class AbstractElasticsearchIT {

  private static final String           IMAGE = System.getProperty("es.testcontainer.image",
                                                                   "docker.elastic.co/elasticsearch/elasticsearch:9.3.4");

  private static ElasticsearchContainer container;

  private static String                 url;

  private static RuntimeException       unavailable;

  protected static synchronized String elasticsearchUrl() {
    if (unavailable != null) {
      // Memoised on purpose: a machine with no Docker pays Testcontainers'
      // discovery timeout once per build rather than once per test method.
      throw unavailable;
    }
    if (url == null) {
      String configured = System.getProperty("es.url");
      if (StringUtils.isNotBlank(configured)) {
        url = configured;
      } else {
        container = new ElasticsearchContainer(DockerImageName.parse(IMAGE))
                                                                            // No credentials, like the engine the
                                                                            // platform runs beside it.
                                                                            .withEnv("xpack.security.enabled", "false")
                                                                            .withEnv("discovery.type", "single-node")
                                                                            .withStartupTimeout(Duration.ofMinutes(5));
        try {
          container.start();
        } catch (RuntimeException e) {
          IllegalStateException failure =
                                        new IllegalStateException("Could not start " + IMAGE
                                            + ". These tests execute hand-written Elasticsearch requests and cannot"
                                            + " be faked; install Docker, or point the build at a running engine with"
                                            + " -Des.url.", e);
          if (!DockerClientFactory.instance().isDockerAvailable()) {
            // Only the deterministic failure is memoised; a start that failed
            // with Docker present is usually transient and worth retrying.
            unavailable = failure;
          }
          container = null;
          throw failure;
        }
        url = "http://" + container.getHttpHostAddress();
      }
    }
    return url;
  }

}
