package net.taskwolf.webhook;

import lombok.RequiredArgsConstructor;
import net.taskwolf.core.workflow.component.input.InputComponentSelect;
import net.taskwolf.webhook.structure.WebhookDatabaseTable;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RequiredArgsConstructor(staticName = "create")
public class WebhookComponentSelect implements InputComponentSelect {
  private final WebhookDatabaseTable webhookDatabaseTable;

  @Override
  public CompletableFuture<List<String>> compile(
    UUID id, Map<String, String> previousInputs
  ) {
    return webhookDatabaseTable.findWebhooksByOwner(id).thenApply(webhooks ->
      webhooks.stream().map(webhook -> new JSONObject(Map.of("identifier",
          webhook.id(), "name", webhook.name())).toString())
        .collect(Collectors.toList()));
  }
}
