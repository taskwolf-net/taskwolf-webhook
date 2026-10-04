package net.taskwolf.webhook.select;

import net.taskwolf.webhook.structure.WebhookDatabaseTable;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.user.User;
import net.taskwolf.workflow.component.input.InputComponentSelect;
import net.taskwolf.workflow.component.input.InputComponentSelectEntry;

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
