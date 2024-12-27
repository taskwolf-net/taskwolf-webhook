package com.dulno.webhook.select;

import lombok.RequiredArgsConstructor;
import com.dulno.core.user.User;
import com.dulno.workflow.component.input.InputComponentSelect;
import com.dulno.workflow.component.input.InputComponentSelectEntry;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RequiredArgsConstructor(staticName = "create")
public final class WebhookMethodComponentSelect implements InputComponentSelect {
  @Override
  public CompletableFuture<List<InputComponentSelectEntry>> compile(
    User user, UUID target, Map<String, String> previousInputs
  ) {
    return CompletableFuture.completedFuture(
      List.of("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "PATCH").stream()
        .map(entry -> InputComponentSelectEntry.create(entry, entry))
        .collect(Collectors.toList()));
  }
}
