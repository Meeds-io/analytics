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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.analytics.elasticsearch.model.AnalyticsMigrationRunningException;
import io.meeds.analytics.elasticsearch.model.ElasticsearchResponse;
import io.meeds.analytics.elasticsearch.storage.ElasticsearchAnalyticsStorage;
import io.meeds.analytics.elasticsearch.storage.ElasticsearchConfiguration;

/**
 * Gives the weekly indices behind the analytics alias the {@code keyword}
 * type of the fields the index template types so. An index template types
 * the indices created after it only, so an index created while the template
 * typed a field as a number keeps that type once the template types it
 * {@code keyword}, and the engine then answers a grouping on the field from
 * the indices of one type only and counts a value once per type among its
 * distinct values, with no error. The type of a field cannot be changed in an
 * index that holds documents: the index is copied into one created with the
 * expected types, which takes its place behind the alias and then its name.
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
 * One run at a time: a run holds a lock document the engine creates for one
 * caller only, renews it while it works and releases it when it ends, each
 * time for the version of the document it last wrote. A run that finds the
 * lock held does nothing, unless the lock was not renewed for
 * {@value #LOCK_EXPIRY_MS} ms, which a run that was stopped leaves behind, and
 * is told after how long such a lock can be taken over; a
 * run whose lock was taken over learns it at its next renewal and stops. The
 * lock is renewed right before each request that deletes an index, so a run
 * that lost it stops before that request and leaves the week in progress to
 * the other run. A second run in the same server is refused before it reads
 * the lock, whose state this service keeps for one run.
 * Each deletion of an index that is not part of an exchange first reads
 * whether the copy carries the alias: the copy is then the one that holds
 * the week.
 * <p>
 * The lock relies on two things. Its age is the time of the node that reads
 * it minus the time of the node that wrote it: the nodes of a cluster keep
 * their clocks synchronised, as the clustered job store already requires, to
 * well within the expiry. And a run that is paused for longer than the
 * expiry between a renewal and the request that follows it still sends that
 * request after another run took the lock over: the two then work on the
 * same week, and the reading of the alias before a deletion narrows that
 * case without closing it.
 * <p>
 * Only a field the index template types {@code keyword}, and that the newest
 * index holds as such, is changed: any value can be held as a keyword. A
 * field the template does not name keeps its type, whatever the newest index
 * gives it, and the difference is reported.
 * <p>
 * What stays open while an index is migrated: a field the storage maps on
 * the alias during the copy is not mapped on the migrated index, which holds
 * no document with it since only the newest index is written; the retention,
 * which deletes the oldest weekly indices at each start and at each new
 * weekly index, can delete the clone before the last exchange, which then
 * fails and leaves the alias to the copy: the next run clones it again, and
 * the retention deletes that week at its next pass; and a last exchange the
 * engine refuses leaves the clone under the weekly name, where the next
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

  /** A lock that was not renewed for that long was left by a stopped run */
  public static final long              LOCK_EXPIRY_MS         = 600000;

  private static final Log              LOG                    = ExoLogger.getLogger(AnalyticsIndexTypeMigrationService.class);

  private static final String           REINDEX_ACTION         = "indices:data/write/reindex";

  private static final String           LOCK_DOCUMENT          = "/_doc/run";

  private static final String           LOCK_CONFLICT          = "version_conflict_engine_exception";

  private static final String           TIMESTAMP              = "timestamp";

  private static final String           PROPERTIES             = "properties";

  private static final String           MAPPINGS               = "mappings";

  private static final String           SETTINGS               = "settings";

  private static final String           NUMBER_OF_REPLICAS     = "number_of_replicas";

  private static final String           AUTO_EXPAND_REPLICAS   = "auto_expand_replicas";

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

  /** Whether a run is in progress in this server */
  private final AtomicBoolean           running                = new AtomicBoolean();

  /** The lock index of the run in progress */
  private String                        lock;

  /** Whether this run holds the lock, and the version of its last write */
  private boolean                       lockHeld;

  private long                          lockSeqNo;

  private long                          lockPrimaryTerm;

  /**
   * Lists the weekly indices that hold, as another type, a field the index
   * template types {@code keyword} and the newest index holds as such, then
   * migrates them, oldest first. The mappings of an index and of the newest
   * one are read again at its turn, which can come long after the list was
   * made: an index the retention deleted meanwhile, or that has the expected
   * types by then, is skipped. An index that cannot be migrated, whether the
   * engine refuses a request or does not answer it, is left as it is behind
   * the alias, and the next ones are still handled.
   *
   * @return                                    the indices that could not be
   *                                            migrated, empty when none is
   *                                            left to migrate
   * @throws AnalyticsMigrationRunningException when another run is in
   *                                              progress in this server or
   *                                              holds the lock, and nothing
   *                                              was read nor changed (the
   *                                              exception then tells after
   *                                              how long a lock left by a
   *                                              stopped run can be taken
   *                                              over), or
   *                                              took the lock over, and this
   *                                              run stopped at its next
   *                                              renewal
   */
  public List<String> migrate() {
    if (!running.compareAndSet(false, true)) {
      throw new AnalyticsMigrationRunningException("The migration of the analytics indices is running in this server");
    }
    try {
      return migrateWeeklyIndices();
    } finally {
      running.set(false);
    }
  }

  private List<String> migrateWeeklyIndices() {
    String prefix = configuration.getIndexPrefix();
    String alias = configuration.getIndexAlias();
    lock = MIGRATION_INDEX_PREFIX + prefix + "-lock";
    long start = System.currentTimeMillis();
    takeLock();
    try {
      LOG.info("Start upgrade: migration of the analytics indices to the keyword fields of the index template");
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
          renewLock();
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
        } catch (AnalyticsMigrationRunningException e) {
          throw e;
        } catch (Exception e) { // NOSONAR the storage throws the client's checked exceptions undeclared
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
    } finally {
      releaseLock();
    }
  }

  /**
   * The newest index is the one written. While it holds, as numbers, the
   * fields the index template types {@code keyword}, nothing is migrated:
   * this is the state of a platform at its first start on a version whose
   * template changed these types, where the index of the current week was
   * created by the former template. The migration is complete only once the
   * newest index has the template's types, which the first index created by
   * the new template brings.
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
    for (String field : getTemplateKeywordFields()) {
      if (!KEYWORD_TYPE.equals(newestProperties.optJSONObject(field, new JSONObject()).optString(TYPE))) {
        LOG.info("Analytics index '{}', the newest one, does not type '{}' as the index template does: the older indices are migrated once an index created by the template is the newest one",
                 newestIndex,
                 field);
        return false;
      }
    }
    return true;
  }

  /**
   * @return the fields the index template types {@code keyword}: the only
   *         ones a migration changes
   */
  private TreeSet<String> getTemplateKeywordFields() {
    JSONObject templateProperties = new JSONObject(configuration.getIndexTemplateMapping()).getJSONObject("template")
                                                                                           .getJSONObject(MAPPINGS)
                                                                                           .getJSONObject(PROPERTIES);
    TreeSet<String> fields = new TreeSet<>();
    for (String field : templateProperties.keySet()) {
      if (KEYWORD_TYPE.equals(templateProperties.getJSONObject(field).optString(TYPE))) {
        fields.add(field);
      }
    }
    return fields;
  }

  /**
   * The engine creates a document for one caller only: the others get a
   * conflict. A lock that was not renewed for {@value #LOCK_EXPIRY_MS} ms is
   * taken over, by one caller only too.
   */
  private void takeLock() {
    lockHeld = false;
    createLockIndex();
    if (createLock()) {
      return;
    }
    ElasticsearchResponse read = storage.sendGetRequest(lock + LOCK_DOCUMENT, false);
    if (read.getStatusCode() == 404) {
      // Released since the conflict
      if (createLock()) {
        return;
      }
      throw new AnalyticsMigrationRunningException("The migration of the analytics indices is running: its lock was just taken");
    } else if (read.getStatusCode() != 200) {
      throw new IllegalStateException("The lock of the migration of the analytics indices cannot be read: " + read.getMessage());
    }
    JSONObject held = new JSONObject(read.getMessage());
    long heldFor = System.currentTimeMillis() - held.getJSONObject("_source").getLong(TIMESTAMP);
    if (heldFor < LOCK_EXPIRY_MS) {
      // A run that was stopped leaves such a lock too: it can be taken over
      // once it is old enough
      throw new AnalyticsMigrationRunningException("The migration of the analytics indices is running, or was stopped: its lock was renewed "
          + heldFor + " ms ago", LOCK_EXPIRY_MS - Math.max(heldFor, 0));
    }
    lockSeqNo = held.getLong("_seq_no");
    lockPrimaryTerm = held.getLong("_primary_term");
    writeLock("The migration of the analytics indices is running: its lock was just taken over");
    LOG.info("The lock of the migration of the analytics indices was not renewed for {} ms: it is taken over", heldFor);
  }

  /**
   * Created here, and not by the first write of the lock: an index the engine
   * creates by itself takes a replica, which a single node never assigns.
   */
  private void createLockIndex() {
    if (exists(lock)) {
      return;
    }
    try {
      storage.sendPutRequest(lock, "{\"settings\":{\"index\":{\"number_of_shards\":1,\"auto_expand_replicas\":\"0-1\"}}}");
    } catch (IllegalStateException e) {
      if (!StringUtils.contains(e.getMessage(), "resource_already_exists_exception")) {
        throw e;
      }
    }
  }

  /**
   * @return true when the engine created the lock document for this run,
   *         false when it exists already
   */
  private boolean createLock() {
    try {
      keepLock(storage.sendPutRequest(lock + "/_create/run?refresh=true", lockContent()));
      return true;
    } catch (IllegalStateException e) {
      if (StringUtils.contains(e.getMessage(), LOCK_CONFLICT)) {
        return false;
      }
      throw e;
    }
  }

  /**
   * Writes the lock for the version this run read or wrote last: the engine
   * refuses it once another run wrote the document.
   */
  private void writeLock(String refusal) {
    try {
      keepLock(storage.sendPutRequest(lock + LOCK_DOCUMENT + "?refresh=true&if_seq_no=" + lockSeqNo + "&if_primary_term="
          + lockPrimaryTerm, lockContent()));
    } catch (IllegalStateException e) {
      if (StringUtils.contains(e.getMessage(), LOCK_CONFLICT)) {
        lockHeld = false;
        throw new AnalyticsMigrationRunningException(refusal);
      }
      throw e;
    }
  }

  private void keepLock(ElasticsearchResponse written) {
    JSONObject version = new JSONObject(written.getMessage());
    lockSeqNo = version.getLong("_seq_no");
    lockPrimaryTerm = version.getLong("_primary_term");
    lockHeld = true;
  }

  private void renewLock() {
    writeLock("The lock of the migration of the analytics indices was taken over by another run: this one stops");
  }

  private void releaseLock() {
    if (!lockHeld) {
      return;
    }
    lockHeld = false;
    try {
      storage.sendDeleteRequest(lock + LOCK_DOCUMENT + "?refresh=true&if_seq_no=" + lockSeqNo + "&if_primary_term="
          + lockPrimaryTerm);
    } catch (Exception e) { // NOSONAR the storage throws the client's checked exceptions undeclared
      LOG.warn("The lock of the migration of the analytics indices could not be released: the next run takes it over once it is {} ms old",
               LOCK_EXPIRY_MS,
               e);
    }
  }

  private static String lockContent() {
    return new JSONObject().put(TIMESTAMP, System.currentTimeMillis()).toString();
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
        renewLock();
        // Read now, and not from the list: a copy is deleted only while it
        // does not carry the alias
        if (carriesAlias(copy, alias)) {
          // The original was deleted in the request that gave the alias to
          // the copy: only the return to its name is left to do
          LOG.info("Analytics index '{}': resuming its migration from its copy '{}'", index, copy);
          takeOriginalName(index, copy, alias);
        } else if (exists(copy)) {
          LOG.info("Analytics index '{}': deleting its unfinished copy '{}'", index, copy);
          cancelCopies(copy);
          storage.sendDeleteRequest(copy);
        }
      } catch (AnalyticsMigrationRunningException e) {
        throw e;
      } catch (Exception e) { // NOSONAR the storage throws the client's checked exceptions undeclared
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
    putReplicas(copySettings, indexSettings);
    if (indexSettings.has("mapping")) {
      // A raised limit on the number of fields, without which the engine
      // refuses the mappings of an index that passed the default one
      copySettings.put("mapping", indexSettings.getJSONObject("mapping"));
    }
    // Nothing reads the copy while it fills
    copySettings.put("refresh_interval", "-1");
    JSONObject copyDefinition = new JSONObject();
    copyDefinition.put(SETTINGS, new JSONObject().put(INDEX, copySettings));
    copyDefinition.put(MAPPINGS, mappings);
    storage.sendPutRequest(copy, copyDefinition.toString());

    copyDocuments(index, copy);
    storage.sendPostRequest(copy + "/_refresh", null);
    checkSameCount(index, copy);

    // The original is deleted by this run only while it holds the lock
    renewLock();
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
      // While the copy carries the alias, the original is deleted and an
      // index of that name is a clone a previous run did not finish. Read
      // now: a copy without the alias, or gone, would mean that this index
      // is the one that holds the week.
      if (!carriesAlias(copy, alias)) {
        throw new IllegalStateException("The copy " + copy + " does not carry the alias any more: " + index + " is left as it is");
      }
      renewLock();
      storage.sendDeleteRequest(index);
    }
    // The engine clones an index only once its writes are blocked
    storage.sendPutRequest(copy + "/_settings", "{\"index.blocks.write\":true}");
    // The engine gives a clone the settings of its source but its number of
    // replicas, which it sets to its default, one: a single node never
    // assigns it
    JSONObject copySettings = readIndices(copy + "/_settings").getJSONObject(copy).getJSONObject(SETTINGS).getJSONObject(INDEX);
    JSONObject cloneSettings = new JSONObject();
    putReplicas(cloneSettings, copySettings);
    storage.sendPostRequest(copy + "/_clone/" + index,
                            new JSONObject().put(SETTINGS, new JSONObject().put(INDEX, cloneSettings)).toString());
    try {
      ElasticsearchResponse health = storage.sendGetRequest("_cluster/health/" + index + "?wait_for_status=yellow&timeout=120s");
      if (new JSONObject(health.getMessage()).optBoolean("timed_out", true)) {
        throw new IllegalStateException("The clone " + index + " of " + copy + " is not available");
      }
      checkSameCount(copy, index);
      // The clone has the other settings of the copy: it is written and refreshed
      // again as any weekly index
      storage.sendPutRequest(index + "/_settings", "{\"index.blocks.write\":null,\"index.refresh_interval\":null}");
    } catch (Exception e) { // NOSONAR the storage throws the client's checked exceptions undeclared
      // A clone left under a weekly name would receive the alias with the
      // next weekly index
      deleteUnusableClone(index, copy, alias);
      throw e;
    }
    // The copy is deleted by this run only while it holds the lock
    renewLock();
    storage.sendPostRequest("_aliases", aliasSwap(index, copy, alias));
  }

  /**
   * Gives an index the replicas of another one: its range of replicas when
   * the engine adjusts their number to the nodes, else their number.
   */
  private void putReplicas(JSONObject settings, JSONObject sourceSettings) {
    String replicasRange = sourceSettings.optString(AUTO_EXPAND_REPLICAS, "false");
    if (StringUtils.equals(replicasRange, "false")) {
      settings.put(NUMBER_OF_REPLICAS, sourceSettings.optString(NUMBER_OF_REPLICAS, "0"));
    } else {
      settings.put(AUTO_EXPAND_REPLICAS, replicasRange);
    }
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
        renewLock();
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
      boolean cancelling = false;
      for (String node : nodes.keySet()) {
        JSONObject tasks = nodes.getJSONObject(node).optJSONObject("tasks", new JSONObject());
        for (String task : tasks.keySet()) {
          // The engine describes it as: reindex from [<source>] to [<copy>]
          if (StringUtils.contains(tasks.getJSONObject(task).optString("description"), "[" + copy + "]")) {
            storage.sendPostRequest("_tasks/" + task + "/_cancel", null);
            cancelling = true;
          }
        }
      }
      if (!cancelling) {
        return;
      }
      renewLock();
      pause();
    }
  }

  private void deleteUnfinishedCopy(String index) {
    String copy = MIGRATION_INDEX_PREFIX + index;
    try {
      // The copy of that name is this run's only while it holds the lock
      renewLock();
      if (exists(copy) && !carriesAlias(copy, configuration.getIndexAlias())) {
        cancelCopies(copy);
        storage.sendDeleteRequest(copy);
      }
    } catch (AnalyticsMigrationRunningException e) {
      throw e;
    } catch (Exception e) { // NOSONAR the storage throws the client's checked exceptions undeclared
      LOG.warn("Analytics index '{}': its unfinished copy '{}' could not be deleted, the next run deletes it", index, copy, e);
    }
  }

  /**
   * Deletes a clone that cannot take the place of its copy, only while the
   * copy still carries the alias: the copy then holds the week. Otherwise the
   * clone may be the one that holds it, and is left as it is. A run that lost
   * the lock leaves the clone too, and stops.
   */
  private void deleteUnusableClone(String index, String copy, String alias) {
    try {
      if (carriesAlias(copy, alias)) {
        renewLock();
        storage.sendDeleteRequest(index);
      } else {
        LOG.warn("Analytics index '{}': its copy '{}' does not carry the alias any more, the index is left as it is", index, copy);
      }
    } catch (AnalyticsMigrationRunningException e) {
      throw e;
    } catch (Exception e) { // NOSONAR the storage throws the client's checked exceptions undeclared
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

  private boolean carriesAlias(String index, String alias) {
    JSONObject indices = readIndices(index);
    return indices.has(index) && indices.getJSONObject(index).optJSONObject(ALIASES, new JSONObject()).has(alias);
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
   * Gives the mappings of an index the definition the newest index has of
   * each field the index template types {@code keyword}, when the newest
   * index holds it as such and the index holds it as another type than
   * {@code text}. Every other field keeps its type: one the template does not
   * name can be a {@code keyword} in the newest index only because its first
   * value there was written as a text, and a sum or a maximum on it would
   * then fail on every week. Such differences are reported.
   *
   * @param  indices       the indices behind the alias, or the index and the
   *                         newest one, with their mappings as just read
   * @param  prefix        the weekly index name prefix
   * @param  index         a weekly index
   * @param  retypedFields receives the fields changed
   * @param  keptFields    receives the fields that differ and are left as
   *                         they are
   * @return               the mappings to copy the index with, null when it
   *                       is the newest one, is not behind the alias any
   *                       more, or has no field to change
   */
  private JSONObject getMappingsToMigrate(JSONObject indices,
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
    JSONObject properties = mappings == null ? null : mappings.optJSONObject(PROPERTIES);
    JSONObject newestProperties = newestMappings == null ? null : newestMappings.optJSONObject(PROPERTIES);
    if (properties == null || newestProperties == null) {
      return null;
    }
    TreeSet<String> templateKeywordFields = getTemplateKeywordFields();
    for (String field : new TreeSet<>(properties.keySet())) {
      JSONObject definition = properties.optJSONObject(field);
      JSONObject newestDefinition = newestProperties.optJSONObject(field);
      if (definition == null || newestDefinition == null) {
        continue;
      }
      String type = definition.optString(TYPE, "object");
      String newestType = newestDefinition.optString(TYPE, "object");
      if (type.equals(newestType)) {
        continue;
      }
      if (templateKeywordFields.contains(field) && KEYWORD_TYPE.equals(newestType) && !TEXT_TYPE.equals(type)
          && definition.has(TYPE)) {
        properties.put(field, new JSONObject(newestDefinition.toString()));
        retypedFields.add(field + ": " + type + " -> " + newestType);
      } else {
        keptFields.add(field + ": " + type + " / " + newestType);
      }
    }
    return retypedFields.isEmpty() ? null : mappings;
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
