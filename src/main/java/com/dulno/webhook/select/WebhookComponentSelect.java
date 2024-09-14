package com.dulno.webhook.select;

import com.dulno.webhook.structure.WebhookDatabaseTable;
import lombok.RequiredArgsConstructor;
import com.dulno.core.user.User;
import com.dulno.core.workflow.component.input.InputComponentSelect;
import com.dulno.core.workflow.component.input.InputComponentSelectEntry;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RequiredArgsConstructor(staticName = "create")
public class WebhookComponentSelect implements InputComponentSelect {
  private final WebhookDatabaseTable webhookDatabaseTable;

  @Override
  public CompletableFuture<List<InputComponentSelectEntry>> compile(
    User user, UUID id, Map<String, String> previousInputs
  ) {
    return webhookDatabaseTable.findAllWebhooksOfOwner(id).thenApply(webhooks ->
      webhooks.stream()
        .map(webhook -> InputComponentSelectEntry.create(webhook.id(), webhook.name()))
        .collect(Collectors.toList()));
  }
}
