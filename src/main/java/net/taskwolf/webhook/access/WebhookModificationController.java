package net.taskwolf.webhook.access;

import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.organization.team.TeamTargetDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import net.taskwolf.webhook.structure.Webhook;
import net.taskwolf.webhook.structure.WebhookDatabaseTable;
import net.taskwolf.webhook.structure.WebhookURL;
import net.taskwolf.core.bundle.BundleDatabaseTable;
import org.springframework.web.bind.annotation.*;

import java.security.Key;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
public final class WebhookModificationController extends WebhookController {
  private final BundleDatabaseTable bundleDatabaseTable;
  private final CoreModule coreModule;
  private final Random random = new Random();
  private final SimpleDateFormat webhookTime = new SimpleDateFormat("HH:mm:ss");
  private final SimpleDateFormat webhookDate = new SimpleDateFormat("dd.MM.yyyy");

  private WebhookModificationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    WebhookDatabaseTable webhookDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable, CoreModule coreModule
  ) {
    super(secretKey, userDatabaseTable, webhookDatabaseTable,
      userTargetDatabaseTable, teamTargetDatabaseTable);
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.coreModule = coreModule;
    this.webhookTime.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
    this.webhookDate.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
  }

  @RequestMapping(path = "/webhook/add/", method = RequestMethod.POST)
  public CompletableFuture<Void> addWebhook(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    return findUser(request).thenCompose(user ->
      userTargetDatabaseTable().findTargetSecured(user.id()).thenCompose(target ->
        findWebhookOwner(user, target).thenCompose(owner ->
          webhookDatabaseTable().generateAvailableWebhookId().thenCompose(id ->
            checkWebhookNumberLimit(target).thenAccept(limitReached ->
              addWebhook(user, owner, body.getString("name"), id, limitReached,
                response))))));
  }

  private CompletableFuture<UUID> findWebhookOwner(User user, UUID target) {
    return user.id().equals(target) ?
      CompletableFuture.completedFuture(target) :
      teamTargetDatabaseTable().findTargetSecured(user.id())
        .thenApply(team -> team.orElse(target));
  }

  private CompletableFuture<Boolean> checkWebhookNumberLimit(UUID target) {
    return bundleDatabaseTable.findBundle(target).thenCompose(bundle ->
      webhookDatabaseTable().findWebhooksByOwner(target).thenApply(
        webhooks -> bundle.webhookNumberLimit() > 0 &&
          webhooks.size() >= bundle.webhookNumberLimit()));
  }

  private void addWebhook(
    User creator, UUID ownerId, String name, String webhookId,
    boolean limitReached, HttpServletResponse response
  ) {
    if (limitReached) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    var created = System.currentTimeMillis();
    webhookDatabaseTable().insertWebhook(webhookId, creator.id(), ownerId, created,
      name, 0, createWebhookKey());
  }

  @RequestMapping(path = "/webhook/trigger/{id}/", method = RequestMethod.POST)
  public CompletableFuture<Void> triggerWebhook(
    HttpServletRequest request, @PathVariable("id") String id,
    @RequestBody String payload, HttpServletResponse response
  ) {
    var key = request.getHeader("Authorization").replace("Bearer", "")
      .replace(" ", "");
    return webhookDatabaseTable().webhookExists(id).thenCompose(exists ->
      triggerWebhook(id, key, payload, exists, response));
  }

  public CompletableFuture<Void> triggerWebhook(
    String webhookId, String key, String body, boolean exists,
    HttpServletResponse response
  ) {
    if (!exists) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return CompletableFuture.completedFuture(null);
    }
    return webhookDatabaseTable().findWebhook(webhookId).thenAccept(webhook ->
      triggerWebhook(webhook, key, body, response));
  }

  public void triggerWebhook(
    Webhook webhook, String key, String body, HttpServletResponse response
  ) {
    if (!webhook.key().equals(key)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    triggerWebhook(webhook, body);
  }

  public void triggerWebhook(
    Webhook webhook, String body
  ) {
    var time = System.currentTimeMillis();
    var information = Map.<String, Object>of("webhookId", webhook.id(),
      "webhookUrl", WebhookURL.create(webhook).build(), "webhookBody", body,
      "webhookFormattedTime", webhookTime.format(new Date(time)),
      "webhookFormattedDate", webhookDate.format(time), "webhookUnixTime", time);
    coreModule.triggerWorkflows("webhook", "webhook-trigger",
      "webhook='" + webhook.id() + "'", information);
    webhookDatabaseTable().useWebhook(webhook);
  }

  @RequestMapping(path = "/webhook/rename/", method = RequestMethod.POST)
  public void renameWebhook(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var webhookId = body.getString("webhook");
    performWebhookOperation(findUserId(request), body.getString("webhook"),
      webhook -> webhookDatabaseTable().renameWebhook(webhook,
        body.getString("name")), () -> {});
  }

  @RequestMapping(path = "/webhook/key/regenerate/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> regenerateWebhookKey(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    performWebhookOperation(findUserId(request), body.getString("webhook"),
      webhook -> futureResponse.complete(regenerateWebhookKey(webhook)),
      () -> {});
    return futureResponse;
  }

  private Map<String, Object> regenerateWebhookKey(
    Webhook webhook
  ) {
    var key = createWebhookKey();
    webhookDatabaseTable().changeWebhookKey(webhook, key);
    return Map.of("key", key);
  }

  private static final String CHARACTERS = "abcdefghijklmnopqrstuvwxyz";

  private String createWebhookKey() {
    var value = new StringBuilder();
    for (int i = 0; i < 32; i++) {
      value.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
    }
    return value.toString();
  }

  @RequestMapping(path = "/webhook/remove/", method = RequestMethod.POST)
  public void removeWebhook(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    performWebhookOperation(findUserId(request), body.getString("webhook"),
      webhook -> webhookDatabaseTable().deleteWebhook(webhook.id()), () -> {});
  }
}
