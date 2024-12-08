package com.dulno.webhook.structure;

import com.google.common.collect.Lists;
import com.dulno.core.database.*;
import com.dulno.core.database.condition.DatabaseComparison;
import com.dulno.core.database.condition.DatabaseCondition;
import com.dulno.core.database.paging.DatabaseDirection;
import com.dulno.core.database.paging.DatabaseOrder;
import com.dulno.core.database.paging.DatabasePage;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class WebhookDatabaseTable extends DatabaseTable {
  private static final String TABLE_NAME = "webhook";

  public static WebhookDatabaseTable create(
    DatabaseConnection connection, DatabaseKeyspace keyspace
  ) {
    var columns = Lists.<DatabaseColumn>newArrayList();
    columns.add(DatabaseColumn.create("owner", DatabaseDataType.UUID,
      DatabaseColumn.Type.PARTITION_KEY));
    columns.add(DatabaseColumn.create("id", DatabaseDataType.TEXT,
      DatabaseColumn.Type.CLUSTERING_KEY));
    columns.add(DatabaseColumn.create("creator", DatabaseDataType.UUID));
    columns.add(DatabaseColumn.create("created", DatabaseDataType.BIGINT));
    columns.add(DatabaseColumn.create("name", DatabaseDataType.TEXT));
    columns.add(DatabaseColumn.create("usages", DatabaseDataType.BIGINT));
    columns.add(DatabaseColumn.create("key", DatabaseDataType.TEXT));
    var table = new WebhookDatabaseTable(connection, keyspace, TABLE_NAME, columns);
    table.createIfNotExists();
    table.createIndexIfNotExists("id");
    table.createIndexIfNotExists("name",
      "'org.apache.cassandra.index.sasi.SASIIndex' WITH OPTIONS = " +
        "{'mode': 'CONTAINS', 'analyzer_class': " +
        "'org.apache.cassandra.index.sasi.analyzer.NonTokenizingAnalyzer', " +
        "'case_sensitive': 'false'}");
    table.initializeViews();
    return table;
  }

  private final Random random = new Random();
  private DatabaseTable nameView;
  private DatabaseTable creatorView;
  private DatabaseTable createdView;
  private DatabaseTable usageView;

  private WebhookDatabaseTable(
    DatabaseConnection connection, DatabaseKeyspace keyspace, String name,
    List<DatabaseColumn> columns
  ) {
    super(connection, keyspace, name, columns);
  }

  private void initializeViews() {
    nameView = createMaterializedViewIfNotExists("name_view", "name");
    creatorView = createMaterializedViewIfNotExists("creator_view", "creator");
    createdView = createMaterializedViewIfNotExists("created_view", "created");
    usageView = createMaterializedViewIfNotExists("usage_view", "usages");
  }

  public CompletableFuture<Void> insertWebhook(Webhook webhook) {
    return insertWebhook(webhook.ownerId(), webhook.id(), webhook.creatorId(),
      webhook.created(), webhook.name(), webhook.usages(), webhook.key());
  }

  public CompletableFuture<Void> insertWebhook(
    UUID ownerId, String id, UUID creatorId, long created, String name,
    long usages, String key
  ) {
    return insert(DatabaseRow.of(ownerId, id, creatorId, created, name, usages,
      key));
  }

  public CompletableFuture<Void> useWebhook(Webhook webhook) {
    webhook.use();
    return updateWebhook(webhook);
  }

  public CompletableFuture<Void> renameWebhook(Webhook webhook, String name) {
    webhook.rename(name);
    return updateWebhook(webhook);
  }

  public CompletableFuture<Void> changeWebhookKey(Webhook webhook, String key) {
    webhook.changeKey(key);
    return updateWebhook(webhook);
  }

  private CompletableFuture<Void> updateWebhook(Webhook webhook) {
    return update(DatabaseCondition.of("owner", webhook.ownerId(), "id",
      webhook.id()), DatabaseRow.of(webhook.ownerId(), webhook.id(),
      webhook.creatorId(), webhook.created(), webhook.name(), webhook.usages(),
      webhook.key()));
  }

  public CompletableFuture<Void> deleteWebhook(String webhookId) {
    return findWebhook(webhookId).thenAccept(webhook ->
      delete(DatabaseCondition.of("owner", webhook.ownerId(), "id", webhook.id())));
  }

  public CompletableFuture<String> generateAvailableWebhookId() {
    var futureResponse = new CompletableFuture<String>();
    var id = createWebhookId();
    webhookExists(id).thenApply(exists -> exists ?
      generateAvailableWebhookId().thenApply(futureResponse::complete) :
      CompletableFuture.completedFuture(futureResponse.complete(id)));
    return futureResponse;
  }

  private static final String CHARACTERS = "abcdefghijklmnopqrstuvwxyz";

  private String createWebhookId() {
    var value = new StringBuilder();
    for (int i = 0; i < 32; i++) {
      value.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
    }
    return value.toString();
  }

  public CompletableFuture<Boolean> webhookExists(String webhookId) {
    return exists(DatabaseCondition.of("id", webhookId));
  }

  public CompletableFuture<Webhook> findWebhook(String webhookId) {
    return selectRow(DatabaseCondition.of("id", webhookId)).thenApply(row ->
      Webhook.of(row, this));
  }

  private static final int PAGE_SIZE = 5;

  public CompletableFuture<DatabasePage<Webhook>> findWebhooksOfOwner(
    UUID ownerId, int targetPage, String sortingColumn, DatabaseOrder sortingOrder,
    String search, UUID creatorId, long startTime, long endTime,
    long minimumUsages, long maximumUsages
  ) {
    if (!search.isEmpty()) {
      var condition = DatabaseCondition.of(DatabaseComparison.create("owner", ownerId),
        DatabaseComparison.create("name", "%" + search + "%", DatabaseComparison.Type.LIKE));
      return selectRows(condition, PAGE_SIZE)
        .thenApply(rows -> createWebhookPage(DatabasePage.create(rows, "", 1), this));
    }
    var view = findTargetView(sortingColumn);
    return view.selectPage(ownerId, createWebhookConditions(creatorId, startTime,
          endTime, minimumUsages, maximumUsages),
        sortingOrder, PAGE_SIZE, targetPage)
      .thenApply(page -> createWebhookPage(page, view));
  }

  public CompletableFuture<DatabasePage<Webhook>> findWebhooksOfOwner(
    UUID ownerId, String pageState, DatabaseDirection startingPoint,
    DatabaseDirection direction, String sortingColumn, DatabaseOrder sortingOrder,
    UUID creatorId, long startTime, long endTime, long minimumUsages,
    long maximumUsages
  ) {
    var view = findTargetView(sortingColumn);
    return view.shiftPage(ownerId, createWebhookConditions(creatorId, startTime,
          endTime, minimumUsages, maximumUsages),
        sortingOrder, PAGE_SIZE, pageState, startingPoint, direction)
      .thenApply(page -> createWebhookPage(page, view));
  }

  private DatabaseTable findTargetView(String sortingColumn) {
    if (sortingColumn.equals("name")) {
      return nameView;
    } else if (sortingColumn.equals("creator")) {
      return creatorView;
    } else if (sortingColumn.equals("created")) {
      return createdView;
    } else if (sortingColumn.equals("usages")) {
      return usageView;
    }
    return null;
  }

  private DatabaseCondition createWebhookConditions(
    UUID creatorId, long startTime, long endTime, long minimumUsages,
    long maximumUsages
  ) {
    var comparisons = Lists.<DatabaseComparison>newArrayList();
    if (creatorId != null) {
      comparisons.add(DatabaseComparison.create("creator", creatorId));
    }
    if (startTime > 0) {
      comparisons.add(DatabaseComparison.create("created", startTime,
        DatabaseComparison.Type.GREATER_EQUALS));
    }
    if (endTime > 0) {
      comparisons.add(DatabaseComparison.create("created", endTime,
        DatabaseComparison.Type.SMALLER_EQUALS));
    }
    if (minimumUsages > 0) {
      comparisons.add(DatabaseComparison.create("usages", minimumUsages,
        DatabaseComparison.Type.GREATER_EQUALS));
    }
    if (maximumUsages > 0) {
      comparisons.add(DatabaseComparison.create("usages", maximumUsages,
        DatabaseComparison.Type.SMALLER_EQUALS));
    }
    return DatabaseCondition.create(comparisons);
  }

  private DatabasePage<Webhook> createWebhookPage(
    DatabasePage<DatabaseRow> page, DatabaseTable table
  ) {
    return DatabasePage.create(
      page.content().stream().map(row -> Webhook.of(row, table)).toList(),
      page.pageState(), page.pageNumber());
  }

  public CompletableFuture<Long> findWebhookCount(UUID ownerId) {
    return count(DatabaseCondition.of("owner", ownerId));
  }

  public CompletableFuture<List<Webhook>> findAllWebhooksOfOwner(UUID ownerId) {
    return selectRows(DatabaseCondition.of("owner", ownerId)).thenApply(rows ->
      rows.stream().map(row -> Webhook.of(row, this)).toList());
  }
}
