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
package io.meeds.analytics.elasticsearch.upgrade;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.analytics.elasticsearch.model.ElasticsearchResponse;
import io.meeds.analytics.elasticsearch.storage.ElasticsearchAnalyticsStorage;
import io.meeds.analytics.elasticsearch.storage.ElasticsearchConfiguration;

/**
 * Gives every weekly index behind the analytics alias the field types of the
 * newest one. An index template types the indices created after it only, so
 * an index created while the template typed a field otherwise keeps that
 * type, and the engine then answers a grouping or a distinct count on the
 * field from the indices of one type only, with no error. The type of a field
 * cannot be changed in an index that holds documents: the index is copied
 * into one created with the expected types, which takes its place behind the
 * alias and then its name.
 * <p>
 * One index at a time, in five steps: the copy is created under a name the
 * index template does not match, so that it stays out of the alias while it
 * fills; the documents are copied by the engine and counted; the copy takes
 * the place of the original behind the alias, in the request that deletes the
 * original; the copy is cloned under the original's name; the clone takes the
 * place of the copy behind the alias, in the request that deletes the copy.
 * The alias never holds two indices of the same week, and an index is deleted
 * only once the one that replaces it holds as many documents.
 * <p>
 * A copy that carries the alias says that its original is deleted: this is
 * what a new run reads to resume. A copy without the alias is an unfinished
 * one, deleted and made again; a copy with the alias is cloned again.
 */
@Service
public class AnalyticsIndexTypeMigrationService {

  private static final Log              LOG                    = ExoLogger.getLogger(AnalyticsIndexTypeMigrationService.class);

  /**
   * Starts the name of an index being copied. The index template matches the
   * names starting with the index prefix: a copy named otherwise receives
   * neither its alias nor its mappings.
   */
  public static final String            MIGRATION_INDEX_PREFIX = "migration-";

  private static final String           REINDEX_ACTION         = "indices:data/write/reindex";

  private static final String           PROPERTIES             = "properties";

  private static final String           MAPPINGS               = "mappings";

  private static final String           SETTINGS               = "settings";

  private static final String           ALIASES                = "aliases";

  private static final String           TYPE                   = "type";

  private static final String           TEXT_TYPE              = "text";

  private static final String           INDEX                  = "index";

  private static final String           LIST_OPTIONS           = "?allow_no_indices=true&ignore_unavailable=true";

  @Autowired
  private ElasticsearchAnalyticsStorage storage;

  @Autowired
  private ElasticsearchConfiguration    configuration;

  private long                          taskPollPeriodMs       = 1000;

  /**
   * Lists the weekly indices whose field types differ from the newest index's,
   * then migrates them, oldest first. An index that cannot be migrated is left
   * as it is, behind the alias, and the next ones are still handled.
   *
   * @return the indices that could not be migrated, empty when every index
   *         now has the types of the newest one
   */
  public List<String> migrate() {
    String prefix = configuration.getIndexPrefix();
    String alias = configuration.getIndexAlias();
    long start = System.currentTimeMillis();
    LOG.info("Start upgrade: migration of the analytics indices to the field types of the newest one");
    List<String> failedIndices = new ArrayList<>(resumeInterruptedMigrations(prefix, alias));

    JSONObject indices = readIndices(alias);
    TreeSet<String> weeklyIndices = new TreeSet<>();
    for (String index : indices.keySet()) {
      if (index.startsWith(prefix + "_")) {
        weeklyIndices.add(index);
      }
    }
    int total = weeklyIndices.size();
    // Oldest first, with the mappings each index is copied to
    Map<String, JSONObject> indicesToMigrate = new LinkedHashMap<>();
    String newestIndex = weeklyIndices.pollLast();
    if (newestIndex != null) {
      JSONObject expectedProperties = getProperties(indices.getJSONObject(newestIndex));
      for (String index : weeklyIndices) {
        JSONObject mappings = indices.getJSONObject(index).optJSONObject(MAPPINGS);
        List<String> retypedFields = new ArrayList<>();
        if (mappings != null && !failedIndices.contains(index)) {
          retypeProperties(mappings, expectedProperties, "", retypedFields);
        }
        if (!retypedFields.isEmpty()) {
          LOG.info("Analytics index '{}' is to migrate: {}", index, retypedFields);
          indicesToMigrate.put(index, mappings);
        }
      }
    }
    LOG.info("Total analytics indices: {}, to migrate: {}", total, indicesToMigrate.size());

    int migrated = 0;
    int position = 0;
    for (Map.Entry<String, JSONObject> indexToMigrate : indicesToMigrate.entrySet()) {
      String index = indexToMigrate.getKey();
      position++;
      long indexStart = System.currentTimeMillis();
      try {
        LOG.info("Migrating analytics index '{}' ({}/{})", index, position, indicesToMigrate.size());
        migrateIndex(index, indexToMigrate.getValue(), alias);
        migrated++;
        LOG.info("PROGRESS:: analytics index '{}' migrated in {} ms: {}/{} done, {} failed",
                 index,
                 System.currentTimeMillis() - indexStart,
                 migrated,
                 indicesToMigrate.size(),
                 failedIndices.size());
      } catch (RuntimeException e) {
        LOG.warn("Error migrating analytics index '{}'. It is left as it is and handled again at the next start. Continue to migrate other indices",
                 index,
                 e);
        failedIndices.add(index);
        deleteUnfinishedCopy(index);
      }
    }
    LOG.info("End migration of the analytics indices: total={} to migrate={} succeeded={} error={} {}. It took {} ms",
             total,
             indicesToMigrate.size(),
             migrated,
             failedIndices.size(),
             failedIndices,
             System.currentTimeMillis() - start);
    return failedIndices;
  }

  /**
   * @param  prefix the weekly index name prefix
   * @param  alias  the analytics alias
   * @return        the indices whose interrupted migration could not be
   *                resumed
   */
  private List<String> resumeInterruptedMigrations(String prefix, String alias) {
    List<String> failedIndices = new ArrayList<>();
    JSONObject copies = readIndices(MIGRATION_INDEX_PREFIX + prefix + "_*");
    for (String copy : new TreeSet<>(copies.keySet())) {
      String index = copy.substring(MIGRATION_INDEX_PREFIX.length());
      try {
        JSONObject aliases = copies.getJSONObject(copy).optJSONObject(ALIASES);
        if (aliases != null && aliases.has(alias)) {
          // The original was deleted in the request that gave the alias to
          // the copy: only the return to its name is left to do
          LOG.info("Analytics index {}: resuming its migration from its copy {}", index, copy);
          takeOriginalName(index, copy, alias);
        } else {
          LOG.info("Analytics index {}: deleting its unfinished copy {}", index, copy);
          cancelCopies(copy);
          storage.sendDeleteRequest(copy);
        }
      } catch (RuntimeException e) {
        LOG.error("Analytics index {}: its interrupted migration could not be resumed, it will be handled again at the next start",
                  index,
                  e);
        failedIndices.add(index);
      }
    }
    return failedIndices;
  }

  private void migrateIndex(String index, JSONObject mappings, String alias) {
    String copy = MIGRATION_INDEX_PREFIX + index;
    JSONObject indexSettings = readIndices(index + "/_settings").getJSONObject(index).getJSONObject(SETTINGS).getJSONObject(INDEX);
    JSONObject copySettings = new JSONObject();
    copySettings.put("number_of_shards", indexSettings.optString("number_of_shards", "1"));
    copySettings.put("number_of_replicas", indexSettings.optString("number_of_replicas", "0"));
    // Nothing reads the copy while it fills
    copySettings.put("refresh_interval", "-1");
    JSONObject copyDefinition = new JSONObject();
    copyDefinition.put(SETTINGS, new JSONObject().put(INDEX, copySettings));
    copyDefinition.put(MAPPINGS, mappings);
    storage.sendPutRequest(copy, copyDefinition.toString());

    copyDocuments(index, copy);
    storage.sendPostRequest(copy + "/_refresh", null);
    checkSameCount(index, copy);

    // One request: no search sees the week twice, nor misses it
    storage.sendPostRequest("_aliases", aliasSwap(copy, index, alias));
    takeOriginalName(index, copy, alias);
  }

  /**
   * Clones the copy, which carries the alias, under the name of the original,
   * which is deleted, then gives the alias to the clone in the request that
   * deletes the copy. The retention of the weekly indices reads their names.
   */
  private void takeOriginalName(String index, String copy, String alias) {
    if (exists(index)) {
      // The copy carries the alias, so the original is deleted: an index of
      // that name is a clone a previous run did not finish
      storage.sendDeleteRequest(index);
    }
    // The engine clones an index only once its writes are blocked
    storage.sendPutRequest(copy + "/_settings", "{\"index.blocks.write\":true}");
    storage.sendPostRequest(copy + "/_clone/" + index, null);
    ElasticsearchResponse health = storage.sendGetRequest("_cluster/health/" + index + "?wait_for_status=yellow&timeout=120s");
    if (new JSONObject(health.getMessage()).optBoolean("timed_out", true)) {
      throw new IllegalStateException("The clone " + index + " of " + copy + " is not available");
    }
    checkSameCount(copy, index);
    // The clone has the settings of the copy: it is written and refreshed
    // again as any weekly index
    storage.sendPutRequest(index + "/_settings", "{\"index.blocks.write\":null,\"index.refresh_interval\":null}");
    storage.sendPostRequest("_aliases", aliasSwap(index, copy, alias));
  }

  /**
   * Asks the engine to copy the documents as a task of its own, then waits
   * for it: the request of a long copy would otherwise stay open for its
   * whole duration.
   */
  private void copyDocuments(String index, String copy) {
    JSONObject reindex = new JSONObject();
    reindex.put("source", new JSONObject().put(INDEX, index));
    reindex.put("dest", new JSONObject().put(INDEX, copy));
    ElasticsearchResponse started = storage.sendPostRequest("_reindex?wait_for_completion=false", reindex.toString());
    String task = new JSONObject(started.getMessage()).getString("task");
    JSONObject state;
    do {
      pause();
      state = new JSONObject(storage.sendGetRequest("_tasks/" + task).getMessage());
    } while (!state.optBoolean("completed"));

    JSONObject result = state.optJSONObject("response");
    JSONArray failures = result == null ? null : result.optJSONArray("failures");
    if (state.has("error") || result == null || (failures != null && !failures.isEmpty())) {
      throw new IllegalStateException("The copy of " + index + " to " + copy + " failed: " + state);
    }
  }

  /**
   * A copy asked by a run that was stopped keeps running in the engine, and
   * would write again into a copy deleted meanwhile.
   */
  private void cancelCopies(String copy) {
    JSONObject nodes;
    do {
      nodes = new JSONObject(storage.sendGetRequest("_tasks?actions=" + REINDEX_ACTION + "&detailed=true").getMessage())
                                                                                                         .optJSONObject("nodes");
      boolean running = false;
      for (String node : nodes == null ? new TreeSet<String>() : nodes.keySet()) {
        JSONObject tasks = nodes.getJSONObject(node).optJSONObject("tasks");
        for (String task : tasks == null ? new TreeSet<String>() : tasks.keySet()) {
          if (StringUtils.contains(tasks.getJSONObject(task).optString("description"), "[" + copy + "]")) {
            storage.sendPostRequest("_tasks/" + task + "/_cancel", null);
            running = true;
          }
        }
      }
      if (!running) {
        return;
      }
      pause();
    } while (true);
  }

  private void deleteUnfinishedCopy(String index) {
    String copy = MIGRATION_INDEX_PREFIX + index;
    try {
      JSONObject copies = readIndices(copy);
      if (copies.has(copy) && !copies.getJSONObject(copy).optJSONObject(ALIASES, new JSONObject()).has(configuration.getIndexAlias())) {
        cancelCopies(copy);
        storage.sendDeleteRequest(copy);
      }
    } catch (RuntimeException e) {
      LOG.warn("Analytics index {}: its unfinished copy {} could not be deleted, the next start deletes it", index, copy, e);
    }
  }

  private void checkSameCount(String source, String target) {
    long sourceCount = count(source);
    long targetCount = count(target);
    if (sourceCount != targetCount) {
      throw new IllegalStateException(target + " holds " + targetCount + " documents while " + source + " holds "
          + sourceCount);
    }
  }

  private long count(String index) {
    return new JSONObject(storage.sendGetRequest(index + "/_count").getMessage()).getLong("count");
  }

  private boolean exists(String index) {
    return storage.sendGetRequest(index + LIST_OPTIONS, false).getStatusCode() == 200 && readIndices(index).has(index);
  }

  /**
   * @param  name an index name, pattern or alias, or a path under one
   * @return      the engine's answer by index name, empty when none matches
   */
  private JSONObject readIndices(String name) {
    String uri = name.contains("/") ? name : name + LIST_OPTIONS;
    return new JSONObject(storage.sendGetRequest(uri).getMessage());
  }

  private static String aliasSwap(String added, String deleted, String alias) {
    JSONObject add = new JSONObject().put(INDEX, added).put("alias", alias).put("is_write_index", false);
    JSONArray actions = new JSONArray();
    actions.put(new JSONObject().put("add", add));
    actions.put(new JSONObject().put("remove_index", new JSONObject().put(INDEX, deleted)));
    return new JSONObject().put("actions", actions).toString();
  }

  private static JSONObject getProperties(JSONObject index) {
    JSONObject mappings = index.optJSONObject(MAPPINGS);
    JSONObject properties = mappings == null ? null : mappings.optJSONObject(PROPERTIES);
    return properties == null ? new JSONObject() : properties;
  }

  /**
   * Gives each field of the mappings the definition the newest index has of
   * it, when both type it and the types differ. A {@code text} field is left
   * as it is: it is not one a grouping reads, and the mapping view does not
   * count it as a conflict either.
   *
   * @param mappings           the mappings of an index, or of an object field
   * @param expectedProperties the fields of the newest index at the same
   *                             level
   * @param path               the name of the object field, empty for the
   *                             index
   * @param retypedFields      receives the names of the fields changed
   */
  private static void retypeProperties(JSONObject mappings,
                                       JSONObject expectedProperties,
                                       String path,
                                       List<String> retypedFields) {
    JSONObject properties = mappings.optJSONObject(PROPERTIES);
    if (properties == null || expectedProperties == null) {
      return;
    }
    for (String field : new TreeSet<>(properties.keySet())) {
      JSONObject definition = properties.optJSONObject(field);
      JSONObject expectedDefinition = expectedProperties.optJSONObject(field);
      if (definition == null || expectedDefinition == null) {
        continue;
      }
      String type = definition.optString(TYPE, null);
      String expectedType = expectedDefinition.optString(TYPE, null);
      if (type == null && expectedType == null) {
        retypeProperties(definition, expectedDefinition.optJSONObject(PROPERTIES), path + field + ".", retypedFields);
      } else if (type != null && expectedType != null
          && !type.equals(expectedType)
          && !TEXT_TYPE.equals(type)
          && !TEXT_TYPE.equals(expectedType)) {
        properties.put(field, new JSONObject(expectedDefinition.toString()));
        retypedFields.add(path + field + ": " + type + " -> " + expectedType);
      }
    }
  }

  private void pause() {
    try {
      Thread.sleep(taskPollPeriodMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("The migration of the analytics indices was interrupted", e);
    }
  }

}
