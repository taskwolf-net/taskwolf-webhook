package net.taskwolf.webhook.access;

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
import net.taskwolf.webhook.trigger.WebhookTrigger;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.*;

import java.security.Key;
import java.util.Map;
import java.util.UUID;

@RestController
public final class WebhookModificationController extends TaskwolfRestController {
  private final WebhookDatabaseTable webhookDatabaseTable;
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final CoreModule coreModule;

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

  @RequestMapping(path = "/webhook/add/", method = RequestMethod.GET)
  public void addWebhook(HttpServletRequest request) {
    findUser(request).thenAccept(user ->
      userTargetDatabaseTable.findTargetSecured(user.id()).thenAccept(target ->
        webhookDatabaseTable.generateAvailableWebhookId().thenAccept(id ->
          addWebhook(user, target, id))));
  }

  private void addWebhook(User creator, UUID ownerId, String webhookId) {
    var created = System.currentTimeMillis();
    webhookDatabaseTable.insertWebhook(webhookId, creator.id(), ownerId,
      created, 0);
  }

  @RequestMapping(path = "/webhook/trigger/{id}/", method = RequestMethod.GET)
  public void triggerWebhook(
    HttpServletRequest request, @PathVariable("id") String id
  ) {
    findUser(request).thenAccept(user -> webhookDatabaseTable.webhookExists(id)
      .thenAccept(exists -> triggerWebhook(user, id, exists)));
  }

  public void triggerWebhook(User user, String webhookId, boolean exists) {
    if (!exists) {
      return;
    }
    webhookDatabaseTable.findWebhook(webhookId).thenAccept(webhook ->
      triggerWebhook(user, webhook));
  }

  public void triggerWebhook(User user, Webhook webhook) {
    if (!checkWebhookAuthorization(user, webhook)) {
      return;
    }
    var information = Map.<String, Object>of("webhookId", webhook.id(),
      "webhookUrl", WebhookURL.create(webhook).build());
    coreModule.triggerWorkflows("webhook", "webhook-trigger", trigger ->
      WebhookTrigger.of(new JSONObject(trigger.content())).webhookIdentifier()
        .equals(webhook.id()), information);
    webhookDatabaseTable.useWebhook(webhook);
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
