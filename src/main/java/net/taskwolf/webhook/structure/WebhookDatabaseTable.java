package net.taskwolf.webhook.structure;

import com.google.common.collect.Lists;
import net.taskwolf.core.database.*;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class WebhookDatabaseTable extends DatabaseTable {
  private static final String TABLE_NAME = "webhook";

  public static WebhookDatabaseTable create(
    DatabaseConnection connection, DatabaseKeyspace keyspace
  ) {
    var columns = Lists.<DatabaseColumn>newArrayList();
    columns.add(DatabaseColumn.create("id", DatabaseDataType.TEXT,
      DatabaseColumn.Type.PRIMARY_KEY));
    columns.add(DatabaseColumn.create("creator", DatabaseDataType.UUID));
    columns.add(DatabaseColumn.create("owner", DatabaseDataType.UUID));
    columns.add(DatabaseColumn.create("created", DatabaseDataType.BIGINT));
    columns.add(DatabaseColumn.create("name", DatabaseDataType.TEXT));
    columns.add(DatabaseColumn.create("usages", DatabaseDataType.BIGINT));
    columns.add(DatabaseColumn.create("key", DatabaseDataType.TEXT));
    return new WebhookDatabaseTable(connection, keyspace, TABLE_NAME, columns);
  }

  private final Random random = new Random();

  private WebhookDatabaseTable(
    DatabaseConnection connection, DatabaseKeyspace keyspace, String name,
    List<DatabaseColumn> columns
  ) {
    super(connection, keyspace, name, columns);
  }

  public void insertWebhook(Webhook webhook) {
    insertWebhook(webhook.id(), webhook.creatorId(), webhook.ownerId(),
      webhook.created(), webhook.name(), webhook.usages(), webhook.key());
  }

  public void insertWebhook(
    String id, UUID creatorId, UUID ownerId, long created, String name,
    long usages, String key
  ) {
    insert(DatabaseRow.of(id, creatorId, ownerId, created, name, usages, key));
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
    update(DatabaseCell.create(webhook.id()), DatabaseRow.of(webhook.id(),
      webhook.creatorId(), webhook.ownerId(), webhook.created(), webhook.name(),
      webhook.usages(), webhook.key()));
  }

  public void deleteWebhook(String webhookId) {
    delete(DatabaseCell.create(webhookId));
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
    return exists(DatabaseCell.create(webhookId));
  }

  public CompletableFuture<Webhook> findWebhook(String webhookId) {
    return selectRow(DatabaseCell.create(webhookId)).thenApply(Webhook::of);
  }

  public CompletableFuture<List<Webhook>> findWebhooksByOwner(UUID ownerId) {
    return selectRows("owner=" + ownerId  + " ALLOW FILTERING").thenApply(rows ->
      rows.stream().map(Webhook::of).collect(Collectors.toList()));
  }
}
