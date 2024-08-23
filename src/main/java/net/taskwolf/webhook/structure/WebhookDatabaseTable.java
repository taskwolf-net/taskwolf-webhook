package net.taskwolf.webhook.structure;

import com.google.common.collect.Lists;
import net.taskwolf.core.database.*;

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

  public void insertWebhook(Webhook webhook) {
    insertWebhook(webhook.ownerId(), webhook.id(), webhook.creatorId(),
      webhook.created(), webhook.name(), webhook.usages(), webhook.key());
  }

  public void insertWebhook(
    UUID ownerId, String id, UUID creatorId, long created, String name,
    long usages, String key
  ) {
    insert(DatabaseRow.of(ownerId, id, creatorId, created, name, usages, key));
  }

  public void useWebhook(Webhook webhook) {
    webhook.use();
    updateWebhook(webhook);
  }

  public void renameWebhook(Webhook webhook, String name) {
    webhook.rename(name);
    updateWebhook(webhook);
  }

  public void changeWebhookKey(Webhook webhook, String key) {
    webhook.changeKey(key);
    updateWebhook(webhook);
  }

  private void updateWebhook(Webhook webhook) {
    update("owner=" + webhook.ownerId() + " AND id='" + webhook.id() + "'",
      DatabaseRow.of(webhook.ownerId(), webhook.id(), webhook.creatorId(),
        webhook.created(), webhook.name(), webhook.usages(), webhook.key()));
  }

  public void deleteWebhook(String webhookId) {
    findWebhook(webhookId).thenAccept(webhook ->
      delete("owner=" + webhook.ownerId() + " AND id='" + webhook.id() + "'"));
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
    return exists("id='" + webhookId + "'");
  }

  public CompletableFuture<Webhook> findWebhook(String webhookId) {
    return selectRow("id='" + webhookId + "'").thenApply(row ->
      Webhook.of(row, this));
  }

  private static final int PAGE_SIZE = 5;

  public CompletableFuture<DatabasePage<Webhook>> findWebhooksOfOwner(
    UUID ownerId, int targetPage, String sortingColumn, DatabaseOrder sortingOrder,
    String search, UUID creatorId, long startTime, long endTime,
    long minimumUsages, long maximumUsages
  ) {
    if (!search.isEmpty()) {
      return selectRows("owner=" + ownerId + " AND name LIKE '%" + search +
        "%' LIMIT " + PAGE_SIZE)
        .thenApply(rows -> createWebhookPage(DatabasePage.create(rows, "", 1), this));
    }
    var view = findTargetView(sortingColumn);
    return view.selectPage(DatabaseCell.create(ownerId),
        createWebhookConditions(creatorId, startTime, endTime, minimumUsages,
          maximumUsages),
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
    return view.shiftPage(DatabaseCell.create(ownerId),
        createWebhookConditions(creatorId, startTime, endTime, minimumUsages,
          maximumUsages),
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

  private List<String> createWebhookConditions(
    UUID creatorId, long startTime, long endTime, long minimumUsages,
    long maximumUsages
  ) {
    var conditions = Lists.<String>newArrayList();
    if (creatorId != null) {
      conditions.add("creator = " + creatorId);
    }
    if (startTime > 0) {
      conditions.add("created >= " + startTime);
    }
    if (endTime > 0) {
      conditions.add("created <= " + endTime);
    }
    if (minimumUsages > 0) {
      conditions.add("usages >= " + minimumUsages);
    }
    if (maximumUsages > 0) {
      conditions.add("usages <= " + maximumUsages);
    }
    return conditions;
  }

  private DatabasePage<Webhook> createWebhookPage(
    DatabasePage<DatabaseRow> page, DatabaseTable table
  ) {
    return DatabasePage.create(
      page.content().stream().map(row -> Webhook.of(row, table)).toList(),
      page.pageState(), page.pageNumber());
  }

  public CompletableFuture<Long> findWebhookCount(UUID ownerId) {
    return count("owner=" + ownerId);
  }

  public CompletableFuture<List<Webhook>> findAllWebhooksOfOwner(UUID ownerId) {
    return selectRows("owner=" + ownerId).thenApply(rows ->
      rows.stream().map(row -> Webhook.of(row, this)).toList());
  }
}
