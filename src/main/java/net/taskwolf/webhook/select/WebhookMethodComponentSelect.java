package net.taskwolf.webhook.select;

import lombok.RequiredArgsConstructor;
import net.taskwolf.core.user.User;
import net.taskwolf.core.workflow.component.input.InputComponentSelect;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RequiredArgsConstructor(staticName = "create")
public final class WebhookMethodComponentSelect implements InputComponentSelect {
  @Override
  public CompletableFuture<List<String>> compile(
    User user, UUID target, Map<String, String> previousInputs
  ) {
    return CompletableFuture.completedFuture(
      List.of("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "PATCH")
        .stream().map(entry ->
          new JSONObject(Map.of("identifier", entry, "name", entry)).toString())
        .collect(Collectors.toList()));
  }
}
