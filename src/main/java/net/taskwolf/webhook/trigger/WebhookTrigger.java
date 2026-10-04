package net.taskwolf.webhook.trigger;

import net.taskwolf.webhook.structure.WebhookDatabaseTable;
import com.google.common.collect.Lists;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.database.*;
import net.taskwolf.workflow.trigger.Trigger;
import net.taskwolf.workflow.trigger.TriggerContentDatabaseTable;
import net.taskwolf.workflow.trigger.TriggerInformation;
import net.taskwolf.core.database.condition.DatabaseCondition;
import net.taskwolf.workflow.component.input.InputComponentSelect;
import net.taskwolf.workflow.component.input.InputComponentVariable;
import net.taskwolf.workflow.component.output.OutputComponentVariable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public final class WebhookTrigger implements Trigger {
  public static WebhookTrigger create(
    WebhookDatabaseTable webhookDatabaseTable,
    InputComponentSelect webhookComponentSelect,
    DatabaseConnection databaseConnection, DatabaseKeyspace databaseKeyspace
  ) {
    var contentColumns = Lists.<DatabaseColumn>newArrayList();
    contentColumns.add(DatabaseColumn.create("owner", DatabaseDataType.UUID));
    contentColumns.add(DatabaseColumn.create("webhook", DatabaseDataType.TEXT));
    return new WebhookTrigger(webhookDatabaseTable, webhookComponentSelect,
      TriggerContentDatabaseTable.create(databaseConnection, databaseKeyspace,
        "trigger_webhook", contentColumns));
  }

  private final WebhookDatabaseTable webhookDatabaseTable;
  private final InputComponentSelect webhookComponentSelect;
  private final TriggerContentDatabaseTable contentDatabaseTable;

  @Override
  public String type() {
    return "webhook-trigger";
  }

  @Override
  public TriggerInformation information() {
    return TriggerInformation.builder()
      .withName("webhook.trigger.name")
      .withDescription("webhook.trigger.description")
      .withInputVariable(InputComponentVariable.createSelect("webhook.trigger.input.webhook.name",
        "webhookIdentifier", "webhook.trigger.input.webhook.description", webhookComponentSelect))
      .withOutputVariable(OutputComponentVariable.create("webhook.trigger.output.webhook.id", "webhookId"))
      .withOutputVariable(OutputComponentVariable.create("webhook.trigger.output.webhook.url", "webhookUrl"))
      .withOutputVariable(OutputComponentVariable.create("webhook.trigger.output.webhook.body", "webhookBody"))
      .withOutputVariable(OutputComponentVariable.create("webhook.trigger.output.webhook.formatted.time", "webhookFormattedTime"))
      .withOutputVariable(OutputComponentVariable.create("webhook.trigger.output.webhook.formatted.date", "webhookFormattedDate"))
      .withOutputVariable(OutputComponentVariable.create("webhook.trigger.output.webhook.unix.time", "webhookUnixTime"))
      .build();
  }

  @Override
  public void initialize() {
    contentDatabaseTable.createIfNotExists();
    contentDatabaseTable.createIndexIfNotExists("webhook");
  }

  @Override
  public CompletableFuture<Void> insert(
    UUID triggerId, UUID ownerId, Map<String, Object> content
  ) {
    return contentDatabaseTable.insertContent(triggerId, DatabaseRow.of(ownerId,
      content.get("webhookIdentifier")));
  }

  @Override
  public CompletableFuture<Boolean> checkExecution(UUID triggerId) {
    return contentDatabaseTable.findContent(triggerId)
      .thenCompose(row -> webhookDatabaseTable.webhookExists(
        row.findCell(2).stringValue())
        .thenCompose(exists -> checkExecution(row.findCell(1).uuidValue(),
          row.findCell(2).stringValue(), exists)));
  }

  public CompletableFuture<Boolean> checkExecution(
    UUID ownerId, String webhookId, boolean webhookExists
  ) {
    if (!webhookExists) {
      return CompletableFuture.completedFuture(false);
    }
    return webhookDatabaseTable.findWebhook(webhookId)
      .thenApply(webhook -> webhook.ownerId().equals(ownerId));
  }

  @Override
  public CompletableFuture<Map<String, Object>> findContent(UUID triggerId) {
    return contentDatabaseTable.findContent(triggerId).thenApply(row ->
      Map.of("webhookIdentifier", row.findCell(2).stringValue()));
  }

  @Override
  public CompletableFuture<List<UUID>> findEntries(DatabaseCondition condition) {
    return contentDatabaseTable.findContentByCondition(condition).thenApply(
      rows -> rows.stream().map(row -> row.findCell(0).uuidValue()).toList());
  }

  @Override
  public CompletableFuture<Void> delete(UUID triggerId) {
    return contentDatabaseTable.deleteContent(triggerId);
  }
}
