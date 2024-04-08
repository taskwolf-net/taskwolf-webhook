package net.taskwolf.webhook.access;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.iterator.AsyncListIterator;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import net.taskwolf.webhook.structure.Webhook;
import net.taskwolf.webhook.structure.WebhookDatabaseTable;
import net.taskwolf.webhook.structure.WebhookURL;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class WebhookInformationController extends TaskwolfRestController {
  private final WebhookDatabaseTable webhookDatabaseTable;
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy");

  private WebhookInformationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    WebhookDatabaseTable webhookDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.webhookDatabaseTable = webhookDatabaseTable;
    this.userTargetDatabaseTable = userTargetDatabaseTable;
  }

  @RequestMapping(path = "/webhook/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findWebhook(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user ->
      webhookDatabaseTable.findWebhook(body.getString("webhook"))
        .thenAccept(webhook -> findWebhook(user, webhook)
          .thenAccept(futureResponse::complete)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> findWebhook(
    User user, Webhook webhook
  ) {
    if (!checkWebhookAuthorization(user, webhook)) {
      var futureResponse = new CompletableFuture<Map<String, Object>>();
      futureResponse.complete(Maps.newHashMap());
      return futureResponse;
    }
    return gatherWebhookInformation(webhook);
  }

  @RequestMapping(path = "/webhooks/selected/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> selectedWebhooks(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenApply(user ->
      userTargetDatabaseTable.findTargetSecured(user.id()).thenAccept(target ->
        findSelectedWebhooks(user, target).thenApply(futureResponse::complete)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> findSelectedWebhooks(
    User user, UUID ownerId
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    if (!checkWebhookAuthorization(user, ownerId)) {
      futureResponse.complete(Maps.newHashMap());
      return futureResponse;
    }
    collectWebhooks(Lists.newArrayList(ownerId)).thenAccept(webhooks ->
      collectWebhooksInformation(webhooks).thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<List<Webhook>> collectWebhooks(
    List<UUID> ownerIds
  ) {
    var futureResponse = new CompletableFuture<List<Webhook>>();
    AsyncListIterator.execute(ownerIds, webhookDatabaseTable::findWebhooksByOwner,
      ownerIds.size(), futureResponse::complete);
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> collectWebhooksInformation(
    List<Webhook> webhooks
  ) {
    if (webhooks.isEmpty()) {
      return CompletableFuture.completedFuture(Map.of("webhooks",
        Lists.newArrayList()));
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    AsyncIterator.execute(webhooks, this::gatherWebhookInformation, webhooks.size(),
      information -> futureResponse.complete(Map.of("webhooks", information)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> gatherWebhookInformation(
    Webhook webhook
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    userDatabaseTable().findUserIfExists(webhook.creatorId())
      .thenAccept(creator -> futureResponse.complete(
        assemblyWebhookInformation(webhook, creator)));
    return futureResponse;
  }

  private Map<String, Object> assemblyWebhookInformation(
    Webhook webhook, User creator
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", webhook.id());
    information.put("name", webhook.name());
    information.put("url", WebhookURL.create(webhook).build());
    information.put("usages", webhook.usages());
    information.put("created", timeMillisecondsToDate(webhook.created()));
    information.put("creator", creator.name());
    information.put("key", webhook.key());
    return information;
  }

  private String timeMillisecondsToDate(long milliseconds) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeInMillis(milliseconds);
    return simpleDateFormat.format(calendar.getTime());
  }

  private boolean checkWebhookAuthorization(User user, Webhook webhook) {
    return checkWebhookAuthorization(user, webhook.ownerId());
  }

  private boolean checkWebhookAuthorization(User user, UUID webhookOwnerId) {
    return webhookOwnerId.equals(user.id()) ||
      user.organizations().contains(webhookOwnerId);
  }
}

