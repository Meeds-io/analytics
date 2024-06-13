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
package io.meeds.analytics.elasticsearch.storage;

import java.io.InputStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.utils.IOUtil;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.SneakyThrows;

@Component
public class ElasticsearchConfiguration {

  private static final Log    LOG                          =
                                  ExoLogger.getLogger(ElasticsearchConfiguration.class);

  private static final String DEFAULT_ES_CLIENT_SERVER_URL = "http://127.0.0.1:9200";

  private static final String ES_CLIENT_SERVER_URL         = "exo.es.index.server.url";

  private static final String ES_CLIENT_USERNAME           = "exo.es.index.server.username";

  private static final String ES_CLIENT_PWD                = "exo.es.index.server.password";   // NOSONAR

  public static final String  ES_ANALYTICS_INDEX_TEMPLATE  = "exo.es.analytics.index.template";

  public static final String  DEFAULT_ES_INDEX_TEMPLATE    = "analytics_template";

  public static final Context ES_ANALYTICS_CONTEXT         = Context.GLOBAL.id("analytics");

  public static final Scope   ES_ANALYTICS_SCOPE           = Scope.APPLICATION.id("analytics");

  @Autowired
  private SettingService      settingService;

  @Getter
  @Value("${analytics.es.index.prefix:analytics}")
  private String              indexPrefix;

  @Getter
  @Value("${analytics.es.index.template:analytics_template}")
  private String              indexTemplateName;

  @Getter
  @Value("${analytics.es.index.alias:analytics_alias}")
  private String              indexAlias;

  @Getter
  @Value("${analytics.es.index.writePeriod:7}")
  private long                indexPerDays;

  @Getter
  @Value("${analytics.es.index.maxCount:500}")
  private long                maxIndexCount;

  @Value("${analytics.es.replicas:0}")
  private int                 replicas;

  @Value("${analytics.es.shards:1}")
  private int                 shards;

  @Value("${analytics.es.index.template.path:analytics-es-template.json}")
  private String              indexTemplateMappingFilePath;

  @Getter
  private String              indexTemplateMapping;

  @Value("${analytics.es.index.server.username:}")
  private String              username;

  @Value("${analytics.es.index.server.password:}")
  private String              password;

  @Getter
  @Value("${analytics.es.index.server.url:}")
  private String              urlClient;

  @SneakyThrows
  @PostConstruct
  public void init() {
    computeConnectionProperties();
    computeIndexTemplateName();
    computeIndexTemplateMapping();
  }

  @Bean("elasticsearchHttpClient")
  private HttpClient elasticsearchHttpClient() {
    // Check if Basic Authentication need to be used
    HttpClientConnectionManager clientConnectionManager = new PoolingHttpClientConnectionManager();
    HttpClientBuilder httpClientBuilder = HttpClientBuilder
                                                           .create()
                                                           .disableAutomaticRetries()
                                                           .setConnectionManager(clientConnectionManager)
                                                           .setConnectionReuseStrategy(new DefaultConnectionReuseStrategy());
    if (StringUtils.isNotBlank(username)) {
      BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
      credsProvider.setCredentials(
                                   new AuthScope(null, -1),
                                   new UsernamePasswordCredentials(username,
                                                                   password.toCharArray()));
      return httpClientBuilder.setDefaultCredentialsProvider(credsProvider)
                              .build();
    } else {
      return httpClientBuilder.build();
    }
  }

  @SneakyThrows
  private void computeIndexTemplateMapping() {
    InputStream mappingFileIS = getClass().getClassLoader().getResourceAsStream("analytics-es-template.json");
    indexTemplateMapping = IOUtil.getStreamContentAsString(mappingFileIS);
    indexTemplateMapping = indexTemplateMapping.replace(ElasticsearchConfiguration.DEFAULT_ES_INDEX_TEMPLATE,
                                                        getIndexAlias())
                                               .replace("replica.number",
                                                        String.valueOf(replicas))
                                               .replace("shard.number",
                                                        String.valueOf(shards));
  }

  private void computeConnectionProperties() {
    if (StringUtils.isBlank(this.urlClient)) {
      this.urlClient = System.getProperty(ES_CLIENT_SERVER_URL);
      this.username = System.getProperty(ES_CLIENT_USERNAME);
      this.password = System.getProperty(ES_CLIENT_PWD);
    }
    if (StringUtils.isBlank(this.urlClient)) {
      this.urlClient = DEFAULT_ES_CLIENT_SERVER_URL;
    }
  }

  private void computeIndexTemplateName() {
    SettingValue<?> indexTemplateValue = this.settingService.get(ES_ANALYTICS_CONTEXT,
                                                                 ES_ANALYTICS_SCOPE,
                                                                 ES_ANALYTICS_INDEX_TEMPLATE);
    if (indexTemplateValue == null || indexTemplateValue.getValue() == null) {
      this.settingService.set(ES_ANALYTICS_CONTEXT,
                              ES_ANALYTICS_SCOPE,
                              ES_ANALYTICS_INDEX_TEMPLATE,
                              SettingValue.create(indexTemplateName));
    } else {
      String storedIndexTemplate = indexTemplateValue.getValue().toString();
      if (!StringUtils.equals(storedIndexTemplate, indexTemplateName)) {
        LOG.warn("Can't change index template from {} to {}. New index will be ignored.", storedIndexTemplate, indexTemplateName);
        indexTemplateName = storedIndexTemplate;
      }
    }
  }
}
