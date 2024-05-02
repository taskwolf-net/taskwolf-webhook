package net.taskwolf.webhook.trigger;

import com.google.common.collect.Lists;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.database.*;
import net.taskwolf.core.trigger.Trigger;
import net.taskwolf.core.trigger.TriggerContentDatabaseTable;
import net.taskwolf.core.trigger.TriggerInformation;
import net.taskwolf.core.workflow.component.input.InputComponentSelect;
import net.taskwolf.core.workflow.component.input.InputComponentVariable;
import net.taskwolf.core.workflow.component.output.OutputComponentVariable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public final class WebhookTrigger implements Trigger {
  public static WebhookTrigger create(
    InputComponentSelect webhookComponentSelect,
    DatabaseConnection databaseConnection, DatabaseKeyspace databaseKeyspace
  ) {
    var contentColumns = Lists.<DatabaseColumn>newArrayList();
    contentColumns.add(DatabaseColumn.create("webhook", DatabaseDataType.UUID));
    return new WebhookTrigger(webhookComponentSelect,
      TriggerContentDatabaseTable.create(databaseConnection, databaseKeyspace,
        "trigger_webhook", contentColumns));
  }

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
      .build();
  }

  @Override
  public CompletableFuture<Void> insert(UUID triggerId, Map<String, Object> content) {
    return contentDatabaseTable.insertContent(triggerId, DatabaseRow.of(
      content.get("webhookIdentifier")));
  }

  @Override
  public CompletableFuture<List<UUID>> findEntries(String condition) {
    return contentDatabaseTable.findContentByCondition(condition).thenApply(
      rows -> rows.stream().map(row -> row.findCell(0).uuidValue()).toList());
  }

  @Override
  public CompletableFuture<Void> delete(UUID triggerId) {
    return contentDatabaseTable.deleteContent(triggerId);
  }
}
