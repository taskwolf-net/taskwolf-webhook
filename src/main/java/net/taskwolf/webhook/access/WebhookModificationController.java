package net.taskwolf.webhook.access;

import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import net.taskwolf.webhook.structure.Webhook;
import net.taskwolf.webhook.structure.WebhookDatabaseTable;
import net.taskwolf.webhook.structure.WebhookURL;
import org.springframework.web.bind.annotation.*;

import java.security.Key;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class WebhookModificationController extends TaskwolfRestController {
  private final WebhookDatabaseTable webhookDatabaseTable;
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final CoreModule coreModule;
  private final Random random = new Random();

  private WebhookModificationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    WebhookDatabaseTable webhookDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    CoreModule coreModule
  ) {
    super(secretKey, userDatabaseTable);
    this.webhookDatabaseTable = webhookDatabaseTable;
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.coreModule = coreModule;
  }

  @RequestMapping(path = "/webhook/add/", method = RequestMethod.POST)
  public void addWebhook(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    findUser(request).thenAccept(user ->
      userTargetDatabaseTable.findTargetSecured(user.id()).thenAccept(target ->
        webhookDatabaseTable.generateAvailableWebhookId().thenAccept(id ->
          addWebhook(user, target, body.getString("name"), id))));
  }

  private void addWebhook(
    User creator, UUID ownerId, String name, String webhookId
  ) {
    var created = System.currentTimeMillis();
    webhookDatabaseTable.insertWebhook(webhookId, creator.id(), ownerId, created,
      name, 0, createWebhookKey());
  }

  @RequestMapping(path = "/webhook/trigger/{id}/", method = RequestMethod.GET)
  public void triggerWebhook(
    HttpServletRequest request, @PathVariable("id") String id
  ) {
    var key = request.getHeader("Authorization").replace("Bearer", "")
      .replace(" ", "");
    webhookDatabaseTable.webhookExists(id).thenAccept(exists ->
      triggerWebhook(id, key, exists));
  }

  public void triggerWebhook(
    String webhookId, String key, boolean exists
  ) {
    if (!exists) {
      return;
    }
    webhookDatabaseTable.findWebhook(webhookId).thenAccept(webhook ->
      triggerWebhook(webhook, key));
  }

  public void triggerWebhook(Webhook webhook, String key) {
    if (!webhook.key().equals(key)) {
      return;
    }
    var information = Map.<String, Object>of("webhookId", webhook.id(),
      "webhookUrl", WebhookURL.create(webhook).build());
    coreModule.triggerWorkflows("webhook", "webhook-trigger",
      "webhook=" + webhook.id(), information);
    webhookDatabaseTable.useWebhook(webhook);
  }

  @RequestMapping(path = "/webhook/rename/", method = RequestMethod.POST)
  public void renameWebhook(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var webhookId = body.getString("webhook");
    findUser(request).thenAccept(user ->
      webhookDatabaseTable.webhookExists(webhookId).thenAccept(exists ->
        renameWebhook(user, webhookId, body.getString("name"), exists)));
  }

  private void renameWebhook(
    User user, String webhookId, String name, boolean webhookExists
  ) {
    if (!webhookExists) {
      return;
    }
    webhookDatabaseTable.findWebhook(webhookId).thenAccept(webhook ->
      renameWebhook(user, webhook, name));
  }

  private void renameWebhook(
    User user, Webhook webhook, String name
  ) {
    if (!checkWebhookAuthorization(user, webhook)) {
      return;
    }
    webhookDatabaseTable.renameWebhook(webhook, name);
  }

  @RequestMapping(path = "/webhook/key/regenerate/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> regenerateWebhookKey(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var webhookId = body.getString("webhook");
    return findUser(request).thenCompose(user ->
      webhookDatabaseTable.webhookExists(webhookId).thenCompose(exists ->
        regenerateWebhookKey(user, webhookId, exists)));
  }

  private CompletableFuture<Map<String, Object>> regenerateWebhookKey(
    User user, String webhookId, boolean webhookExists
  ) {
    if (!webhookExists) {
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    return webhookDatabaseTable.findWebhook(webhookId).thenApply(webhook ->
      regenerateWebhookKey(user, webhook));
  }

  private Map<String, Object> regenerateWebhookKey(
    User user, Webhook webhook
  ) {
    if (!checkWebhookAuthorization(user, webhook)) {
      return Maps.newHashMap();
    }
    var key = createWebhookKey();
    webhookDatabaseTable.changeWebhookKey(webhook, key);
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
    var webhookId = body.getString("webhook");
    findUser(request).thenAccept(user ->
      webhookDatabaseTable.webhookExists(webhookId).thenAccept(exists ->
        deleteWebhook(user, webhookId, exists)));
  }

  private void deleteWebhook(User user, String webhookId, boolean webhookExists) {
    if (!webhookExists) {
      return;
    }
    webhookDatabaseTable.findWebhook(webhookId).thenAccept(webhook ->
      deleteWebhook(user, webhook));
  }

  private void deleteWebhook(User user, Webhook webhook) {
    if (!checkWebhookAuthorization(user, webhook)) {
      return;
    }
    webhookDatabaseTable.deleteWebhook(webhook.id());
  }

  private boolean checkWebhookAuthorization(User user, Webhook webhook) {
    return checkWebhookAuthorization(user, webhook.ownerId());
  }

  private boolean checkWebhookAuthorization(User user, UUID webhookOwnerId) {
    return webhookOwnerId.equals(user.id()) ||
      user.organizations().contains(webhookOwnerId);
  }
}
