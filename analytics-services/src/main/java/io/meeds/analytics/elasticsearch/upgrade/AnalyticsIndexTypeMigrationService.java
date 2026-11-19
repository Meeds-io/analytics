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
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

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
 * Gives the weekly indices behind the analytics alias the {@code keyword}
 * fields of the newest one. An index template types the indices created
 * after it only, so an index created while the template typed a field as a
 * number keeps that type once the template types it {@code keyword}, and the
 * engine then answers a grouping on the field from the indices of one type
 * only and counts a value once per type among its distinct values, with no
 * error. The type of a field cannot be changed in an index that holds
 * documents: the index is copied into one created with the expected types,
 * which takes its place behind the alias and then its name.
 * <p>
 * One index at a time, in five steps: the copy is created under a name the
 * index template does not match, so that it stays out of the alias while it
 * fills; the documents are copied by the engine and counted; the copy takes
 * the place of the original behind the alias, in the request that deletes the
 * original; the copy is cloned under the original's name; the clone takes the
 * place of the copy behind the alias, in the request that deletes the copy.
 * An index is deleted only once the one that replaces it holds as many
 * documents, and each exchange behind the alias is one request, so a search
 * reads a week once. One case reads it twice for a moment: a new weekly
 * index created between the clone and the last exchange, since the storage
 * then gives the alias to every index named after the prefix, the clone
 * included. The last exchange ends it; a clone that is not available or does
 * not hold every document is deleted at once, and the copy keeps the alias.
 * <p>
 * A copy that carries the alias says that its original is deleted: this is
 * what a new run reads to resume. A copy without the alias is an unfinished
 * one, deleted and made again; a copy with the alias is cloned again.
 * <p>
 * Only a field the newest index types {@code keyword} is changed, since any
 * value can be held as a keyword. Any other difference is reported and left
 * as it is: a copy would refuse a text that is not a number, and would change
 * a number given a narrower type.
 * <p>
 * What stays open while an index is migrated: a field the storage maps on
 * the alias during the copy is not mapped on the migrated index, which holds
 * no document with it since only the newest index is written; and the
 * retention, which deletes the oldest weekly indices at each start and at
 * each new weekly index, can delete the clone before the last exchange, which
 * then fails and leaves the alias to the copy: the next run clones it again,
 * and the retention deletes that week at its next pass; and a last exchange
 * the engine refuses leaves the clone under the weekly name, where the next
 * weekly index gives it the alias beside the copy, until the next run, which
 * deletes it and clones the copy again.
 */
@Service
public class AnalyticsIndexTypeMigrationService {

  /**
   * Starts the name of an index being copied. The index template matches the
   * names starting with the index prefix: a copy named otherwise receives
   * neither its alias nor its mappings.
   */
  public static final String            MIGRATION_INDEX_PREFIX = "migration-";

  private static final Log              LOG                    = ExoLogger.getLogger(AnalyticsIndexTypeMigrationService.class);

  private static final String           REINDEX_ACTION         = "indices:data/write/reindex";

  private static final String           PROPERTIES             = "properties";

  private static final String           MAPPINGS               = "mappings";

  private static final String           SETTINGS               = "settings";

  private static final String           ALIASES                = "aliases";

  private static final String           TYPE                   = "type";

  private static final String           TEXT_TYPE              = "text";

  private static final String           KEYWORD_TYPE           = "keyword";

  private static final String           INDEX                  = "index";

  private static final String           LIST_OPTIONS           = "?allow_no_indices=true&ignore_unavailable=true";

  /** The polls of a running copy between two lines of progress */
  private static final int              PROGRESS_POLLS         = 30;

  @Autowired
  private ElasticsearchAnalyticsStorage storage;

  @Autowired
  private ElasticsearchConfiguration    configuration;

  private long                          taskPollPeriodMs       = 1000;

  /**
   * Lists the weekly indices whose fields differ from the {@code keyword}
   * ones of the newest index, then migrates them, oldest first. The mappings
   * of an index and of the newest one are read again at its turn, which can
   * come long after the list was made: an index the retention deleted
   * meanwhile, or that has the expected types by then, is skipped. An index
   * that cannot be migrated is left as it is, behind the alias, and the next
   * ones are still handled.
   *
   * @return the indices that could not be migrated, empty when none is left
   *         to migrate
   */
  public List<String> migrate() {
    String prefix = configuration.getIndexPrefix();
    String alias = configuration.getIndexAlias();
    long start = System.currentTimeMillis();
    LOG.info("Start upgrade: migration of the analytics indices to the keyword fields of the newest one");
    List<String> failedIndices = new ArrayList<>(resumeInterruptedMigrations(prefix, alias));

    JSONObject indices = readIndices(alias);
    TreeSet<String> weeklyIndices = getWeeklyIndices(indices, prefix);
    // Oldest first
    List<String> indicesToMigrate = new ArrayList<>();
    for (String index : weeklyIndices) {
      List<String> retypedFields = new ArrayList<>();
      List<String> keptFields = new ArrayList<>();
      JSONObject mappings = getMappingsToMigrate(indices, prefix, index, retypedFields, keptFields);
      if (!keptFields.isEmpty()) {
        LOG.warn("Analytics index '{}' keeps fields that differ from the ones of the newest index and that are not migrated: {}",
                 index,
                 keptFields);
      }
      if (mappings != null && !failedIndices.contains(index)) {
        LOG.info("Analytics index '{}' is to migrate: {}", index, retypedFields);
        indicesToMigrate.add(index);
      }
    }
    LOG.info("Total analytics indices: {}, to migrate: {}", weeklyIndices.size(), indicesToMigrate.size());

    int migrated = 0;
    int skipped = 0;
    int position = 0;
    for (String index : indicesToMigrate) {
      position++;
      long indexStart = System.currentTimeMillis();
      try {
        // Read again: the list can be hours old when the turn of an index
        // comes, and its copy ends with the deletion of the original
        JSONObject mappings = getMappingsToMigrate(readWithNewestIndex(index, prefix, alias),
                                                   prefix,
                                                   index,
                                                   new ArrayList<>(),
                                                   new ArrayList<>());
        if (mappings == null) {
          skipped++;
          LOG.info("Analytics index '{}' ({}/{}) is not to migrate any more: deleted, or with the expected types",
                   index,
                   position,
                   indicesToMigrate.size());
          continue;
        }
        LOG.info("Migrating analytics index '{}' ({}/{})", index, position, indicesToMigrate.size());
        migrateIndex(index, mappings, alias);
        migrated++;
        LOG.info("PROGRESS:: analytics index '{}' migrated in {} ms: {}/{} processed, {} failed",
                 index,
                 System.currentTimeMillis() - indexStart,
                 position,
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
    LOG.info("End migration of the analytics indices: total={} to migrate={} succeeded={} skipped={} error={} {}. It took {} ms",
             weeklyIndices.size(),
             indicesToMigrate.size(),
             migrated,
             skipped,
             failedIndices.size(),
             failedIndices,
             System.currentTimeMillis() - start);
    return failedIndices;
  }

  /**
   * The newest index is the reference of a run. While it holds, as numbers,
   * the fields the index template types {@code keyword}, every index agrees
   * with it and nothing is migrated: this is the state of a platform at its
   * first start on a version whose template changed these types, where the
   * index of the current week was created by the former template. The
   * migration is complete only once the newest index has the template's
   * types, which the first index created by the new template brings.
   *
   * @return true when the newest weekly index types {@code keyword} every
   *         field the index template types so, or when there is no weekly
   *         index
   */
  public boolean isNewestIndexTypedAsTheTemplate() {
    String prefix = configuration.getIndexPrefix();
    TreeSet<String> weeklyIndices = getWeeklyIndices(readIndices(configuration.getIndexAlias()), prefix);
    if (weeklyIndices.isEmpty()) {
      return true;
    }
    String newestIndex = weeklyIndices.last();
    JSONObject newestProperties = readIndices(newestIndex + "/_mapping").getJSONObject(newestIndex)
                                                                        .getJSONObject(MAPPINGS)
                                                                        .optJSONObject(PROPERTIES, new JSONObject());
    JSONObject templateProperties = new JSONObject(configuration.getIndexTemplateMapping()).getJSONObject("template")
                                                                                           .getJSONObject(MAPPINGS)
                                                                                           .getJSONObject(PROPERTIES);
    for (String field : templateProperties.keySet()) {
      JSONObject newestDefinition = newestProperties.optJSONObject(field, new JSONObject());
      if (KEYWORD_TYPE.equals(templateProperties.getJSONObject(field).optString(TYPE))
          && !KEYWORD_TYPE.equals(newestDefinition.optString(TYPE))) {
        LOG.info("Analytics index '{}', the newest one, does not type '{}' as the index template does: the older indices are migrated once an index created by the template is the newest one",
                 newestIndex,
                 field);
        return false;
      }
    }
    return true;
  }

  /**
   * @param  index  a weekly index
   * @param  prefix the weekly index name prefix
   * @param  alias  the analytics alias
   * @return        the mappings of the index and of the newest weekly index
   *                behind the alias, by index name, as they are now
   */
  private JSONObject readWithNewestIndex(String index, String prefix, String alias) {
    TreeSet<String> weeklyIndices = getWeeklyIndices(readIndices("_alias/" + alias), prefix);
    if (!weeklyIndices.contains(index)) {
      return new JSONObject();
    }
    return readIndices(index + "," + weeklyIndices.last() + "/_mapping?ignore_unavailable=true");
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
    Pattern copyName = Pattern.compile(Pattern.quote(MIGRATION_INDEX_PREFIX) + weeklyIndexName(prefix));
    for (String copy : new TreeSet<>(copies.keySet())) {
      if (!copyName.matcher(copy).matches()) {
        continue;
      }
      String index = copy.substring(MIGRATION_INDEX_PREFIX.length());
      try {
        JSONObject aliases = copies.getJSONObject(copy).optJSONObject(ALIASES);
        if (aliases != null && aliases.has(alias)) {
          // The original was deleted in the request that gave the alias to
          // the copy: only the return to its name is left to do
          LOG.info("Analytics index '{}': resuming its migration from its copy '{}'", index, copy);
          takeOriginalName(index, copy, alias);
        } else {
          LOG.info("Analytics index '{}': deleting its unfinished copy '{}'", index, copy);
          cancelCopies(copy);
          storage.sendDeleteRequest(copy);
        }
      } catch (RuntimeException e) {
        LOG.warn("Error resuming the migration of the analytics index '{}'. It is handled again at the next start", index, e);
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
    try {
      ElasticsearchResponse health = storage.sendGetRequest("_cluster/health/" + index + "?wait_for_status=yellow&timeout=120s");
      if (new JSONObject(health.getMessage()).optBoolean("timed_out", true)) {
        throw new IllegalStateException("The clone " + index + " of " + copy + " is not available");
      }
      checkSameCount(copy, index);
      // The clone has the settings of the copy: it is written and refreshed
      // again as any weekly index
      storage.sendPutRequest(index + "/_settings", "{\"index.blocks.write\":null,\"index.refresh_interval\":null}");
    } catch (RuntimeException e) {
      // The copy carries the alias and holds the week: a clone left under a
      // weekly name would receive the alias with the next weekly index
      deleteUnusableClone(index, copy);
      throw e;
    }
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
    int polls = 0;
    do {
      pause();
      state = new JSONObject(storage.sendGetRequest("_tasks/" + task).getMessage());
      if (++polls % PROGRESS_POLLS == 0 && !state.optBoolean("completed")) {
        JSONObject status = state.optJSONObject("task", new JSONObject()).optJSONObject("status", new JSONObject());
        LOG.info("PROGRESS:: analytics index '{}': {}/{} documents copied", index, status.optLong("created"), status.optLong("total"));
      }
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
    while (true) {
      JSONObject nodes = new JSONObject(storage.sendGetRequest("_tasks?actions=" + REINDEX_ACTION + "&detailed=true")
                                               .getMessage()).optJSONObject("nodes", new JSONObject());
      boolean running = false;
      for (String node : nodes.keySet()) {
        JSONObject tasks = nodes.getJSONObject(node).optJSONObject("tasks", new JSONObject());
        for (String task : tasks.keySet()) {
          // The engine describes it as: reindex from [<source>] to [<copy>]
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
    }
  }

  private void deleteUnfinishedCopy(String index) {
    String copy = MIGRATION_INDEX_PREFIX + index;
    try {
      JSONObject copies = readIndices(copy);
      if (copies.has(copy)
          && !copies.getJSONObject(copy).optJSONObject(ALIASES, new JSONObject()).has(configuration.getIndexAlias())) {
        cancelCopies(copy);
        storage.sendDeleteRequest(copy);
      }
    } catch (RuntimeException e) {
      LOG.warn("Analytics index '{}': its unfinished copy '{}' could not be deleted, the next run deletes it", index, copy, e);
    }
  }

  private void deleteUnusableClone(String index, String copy) {
    try {
      storage.sendDeleteRequest(index);
    } catch (RuntimeException e) {
      LOG.warn("Analytics index '{}': the clone of its copy '{}' could not be deleted, the next run deletes it", index, copy, e);
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
    return readIndices(index).has(index);
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

  /** As the mapping view reads them: an index named otherwise is no week */
  private static String weeklyIndexName(String prefix) {
    return Pattern.quote(prefix) + "_\\d{4}-\\d{2}-\\d{2}";
  }

  private static TreeSet<String> getWeeklyIndices(JSONObject indices, String prefix) {
    Pattern weeklyIndexName = Pattern.compile(weeklyIndexName(prefix));
    TreeSet<String> weeklyIndices = new TreeSet<>();
    for (String index : indices.keySet()) {
      if (weeklyIndexName.matcher(index).matches()) {
        weeklyIndices.add(index);
      }
    }
    return weeklyIndices;
  }

  /**
   * @param  indices       the indices behind the alias, with their mappings,
   *                         as just read
   * @param  prefix        the weekly index name prefix
   * @param  index         a weekly index
   * @param  retypedFields receives the fields changed
   * @param  keptFields    receives the fields that differ and are left as
   *                         they are
   * @return               the mappings to copy the index with, null when it
   *                       is the newest one, is not behind the alias any
   *                       more, or has no field to change
   */
  private static JSONObject getMappingsToMigrate(JSONObject indices,
                                                 String prefix,
                                                 String index,
                                                 List<String> retypedFields,
                                                 List<String> keptFields) {
    TreeSet<String> weeklyIndices = getWeeklyIndices(indices, prefix);
    if (!weeklyIndices.contains(index) || index.equals(weeklyIndices.last())) {
      return null;
    }
    JSONObject mappings = indices.getJSONObject(index).optJSONObject(MAPPINGS);
    JSONObject newestMappings = indices.getJSONObject(weeklyIndices.last()).optJSONObject(MAPPINGS);
    if (mappings == null || newestMappings == null) {
      return null;
    }
    retypeProperties(mappings, newestMappings.optJSONObject(PROPERTIES), "", retypedFields, keptFields);
    return retypedFields.isEmpty() ? null : mappings;
  }

  /**
   * Gives each field of the mappings the definition the newest index has of
   * it, when the newest index types it {@code keyword} and the index types it
   * otherwise. Every other difference is reported and left as it is: a
   * {@code text} field on either side, which no grouping reads and which the
   * mapping view does not count as a conflict either; a field that is an
   * object on one side and a value on the other; a field the newest index
   * gives another type than {@code keyword}, which a copy could refuse or
   * change.
   *
   * @param mappings           the mappings of an index, or of an object field
   * @param expectedProperties the fields of the newest index at the same
   *                             level
   * @param path               the name of the object field, empty for the
   *                             index
   * @param retypedFields      receives the fields changed
   * @param keptFields         receives the fields that differ and are left
   *                             as they are
   */
  private static void retypeProperties(JSONObject mappings,
                                       JSONObject expectedProperties,
                                       String path,
                                       List<String> retypedFields,
                                       List<String> keptFields) {
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
        retypeProperties(definition, expectedDefinition.optJSONObject(PROPERTIES), path + field + ".", retypedFields, keptFields);
      } else if (StringUtils.equals(type, expectedType)) {
        continue;
      } else if (type != null && !TEXT_TYPE.equals(type) && KEYWORD_TYPE.equals(expectedType)) {
        properties.put(field, new JSONObject(expectedDefinition.toString()));
        retypedFields.add(path + field + ": " + type + " -> " + expectedType);
      } else {
        keptFields.add(path + field + ": " + StringUtils.defaultString(type, "object") + " / "
            + StringUtils.defaultString(expectedType, "object"));
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
