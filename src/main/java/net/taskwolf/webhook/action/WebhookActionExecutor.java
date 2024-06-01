package net.taskwolf.webhook.action;

import com.google.common.collect.Maps;
import lombok.AllArgsConstructor;
import net.taskwolf.core.action.ActionExecutor;
import net.taskwolf.core.action.ActionResult;
import net.taskwolf.core.workflow.placeholder.PlaceholderDissolve;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@AllArgsConstructor(staticName = "create")
public final class WebhookActionExecutor implements ActionExecutor {
  private final String method;
  private String url;
  private String headers;
  private String body;

  @Override
  public CompletableFuture<ActionResult> execute(Map<String, Object> information) {
    var dissolve = PlaceholderDissolve.create(information);
    url = dissolve.dissolve(url);
    headers = dissolve.dissolve(headers);
    body = dissolve.dissolve(body);
    var futureResponse = new CompletableFuture<ActionResult>();
    var requestBuilder = HttpRequest.newBuilder().uri(URI.create(url))
      .method(method, HttpRequest.BodyPublishers.ofString(body));
    if (!applyHeaders(requestBuilder)) {
      return ActionResult.futureFailure("webhook.action.failure.wrong.header.schema");
    }
    var httpRequest = requestBuilder.build();
    HttpClient.newHttpClient().sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
      .thenAccept(response -> futureResponse.complete(ActionResult.success(
        buildInformation(response.body(), response.statusCode()))));
    return futureResponse;
  }

  private boolean applyHeaders(HttpRequest.Builder requestBuilder) {
    if (headers.isEmpty()) {
      return true;
    }
    var entries = headers.split(",");
    for (var entry : entries) {
      if (!entry.contains("=")) {
        return false;
      }
      var split = entry.split("=");
      if (split.length != 2) {
        return false;
      }
      var key = split[0];
      var value = split[1];
      requestBuilder.setHeader(key, value);
    }
    return true;
  }

  private Map<String, Object> buildInformation(String response, int statusCode) {
    var information = Maps.<String, Object>newHashMap();
    information.put("method", url);
    information.put("url", method);
    information.put("headers", headers);
    information.put("body", body);
    information.put("response", response);
    information.put("statusCode", statusCode);
    return information;
  }
}

