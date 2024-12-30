package com.dulno.webhook.action;

import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import com.dulno.workflow.action.Action;
import com.dulno.workflow.action.ActionContentDatabaseTable;
import com.dulno.workflow.action.ActionInformation;
import com.dulno.core.database.*;
import com.dulno.workflow.component.input.InputComponentDataType;
import com.dulno.workflow.component.input.InputComponentSelect;
import com.dulno.workflow.component.input.InputComponentVariable;
import com.dulno.workflow.component.output.OutputComponentVariable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@AllArgsConstructor(staticName = "create")
public final class WebhookAction implements Action<WebhookActionExecutor> {
  public static WebhookAction create(
    InputComponentSelect webhookMethodSelect,
    DatabaseConnection databaseConnection, DatabaseKeyspace databaseKeyspace
  ) {
    var contentColumns = Lists.<DatabaseColumn>newArrayList();
    contentColumns.add(DatabaseColumn.create("method", DatabaseDataType.TEXT));
    contentColumns.add(DatabaseColumn.create("url", DatabaseDataType.TEXT));
    contentColumns.add(DatabaseColumn.create("headers", DatabaseDataType.TEXT));
    contentColumns.add(DatabaseColumn.create("body", DatabaseDataType.TEXT));
    return new WebhookAction(webhookMethodSelect, ActionContentDatabaseTable.create(
      databaseConnection, databaseKeyspace, "action_webhook",
      contentColumns));
  }

  private final InputComponentSelect webhookMethodSelect;
  private final ActionContentDatabaseTable contentDatabaseTable;

  @Override
  public String type() {
    return "webhook-action";
  }

  @Override
  public ActionInformation information() {
    return ActionInformation.builder()
      .withName("webhook.action.name")
      .withDescription("webhook.action.description")
      .withInputVariable(InputComponentVariable.createSelect("webhook.action.input.method.name",
        "method", "webhook.action.input.method.description", webhookMethodSelect))
      .withInputVariable(InputComponentVariable.createRequired("webhook.action.input.url.name",
        "url", "webhook.action.input.url.description", InputComponentDataType.TEXT))
      .withInputVariable(InputComponentVariable.createOptional("webhook.action.input.headers.name",
        "headers", "webhook.action.input.headers.description", "webhook.action.input.headers.placeholder",
        InputComponentDataType.TEXT))
      .withInputVariable(InputComponentVariable.createOptional("webhook.action.input.body.name",
        "body", "webhook.action.input.body.description", InputComponentDataType.TEXT))
      .withOutputVariable(OutputComponentVariable.create("webhook.action.output.method", "method"))
      .withOutputVariable(OutputComponentVariable.create("webhook.action.output.url", "url"))
      .withOutputVariable(OutputComponentVariable.create("webhook.action.output.headers", "headers"))
      .withOutputVariable(OutputComponentVariable.create("webhook.action.output.body", "body"))
      .withOutputVariable(OutputComponentVariable.create("webhook.action.output.response", "response"))
      .withOutputVariable(OutputComponentVariable.create("webhook.action.output.status.code", "statusCode"))
      .build();
  }

  @Override
  public void initialize() {
    contentDatabaseTable.createIfNotExists();
  }

  @Override
  public CompletableFuture<Void> insert(
    UUID actionId, UUID ownerId, Map<String, Object> content
  ) {
    return contentDatabaseTable.insertContent(actionId, DatabaseRow.of(
      content.get("method"), content.get("url"), content.get("headers"),
      content.get("body")));
  }

  @Override
  public CompletableFuture<Map<String, Object>> findContent(UUID actionId) {
    return contentDatabaseTable.findContent(actionId).thenApply(row ->
      Map.of("method", row.findCell(1).stringValue(),
        "url", row.findCell(2).stringValue(),
        "headers", row.findCell(3).stringValue(),
        "body", row.findCell(4).stringValue()));
  }

  @Override
  public CompletableFuture<WebhookActionExecutor> build(UUID actionId) {
    return contentDatabaseTable.findContent(actionId).thenApply(content ->
      WebhookActionExecutor.create(content.findCell(1).stringValue(),
        content.findCell(2).stringValue(), content.findCell(3).stringValue(),
        content.findCell(4).stringValue()));
  }

  @Override
  public CompletableFuture<Void> delete(UUID actionId) {
    return contentDatabaseTable.deleteContent(actionId);
  }
}
