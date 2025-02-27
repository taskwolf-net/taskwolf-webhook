package com.dulno.webhook.access;

import com.dulno.webhook.structure.Webhook;
import com.dulno.webhook.structure.WebhookDatabaseTable;
import com.dulno.webhook.structure.WebhookURL;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.workflow.WorkflowModule;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.database.condition.DatabaseCondition;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.organization.team.Team;
import com.dulno.core.organization.team.TeamDatabaseTable;
import com.dulno.core.organization.team.TeamTargetDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserTargetDatabaseTable;
import com.dulno.core.bundle.BundleDatabaseTable;
import org.springframework.web.bind.annotation.*;

import java.security.Key;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@RestController
public final class WebhookModificationController extends WebhookController {
  private final BundleDatabaseTable bundleDatabaseTable;
  private final TeamDatabaseTable teamDatabaseTable;
  private final WorkflowModule workflowModule;
  private final Random random = new Random();
  private final SimpleDateFormat webhookTime = new SimpleDateFormat("HH:mm:ss");
  private final SimpleDateFormat webhookDate = new SimpleDateFormat("dd.MM.yyyy");

  private WebhookModificationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    WebhookDatabaseTable webhookDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable, TeamDatabaseTable teamDatabaseTable,
    WorkflowModule workflowModule
  ) {
    super(secretKey, userDatabaseTable, webhookDatabaseTable,
      userTargetDatabaseTable, teamTargetDatabaseTable);
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.teamDatabaseTable = teamDatabaseTable;
    this.workflowModule = workflowModule;
    this.webhookTime.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
    this.webhookDate.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
  }

  @RequestMapping(path = "/webhook/add/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> addWebhook(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    return findUser(request).thenCompose(user ->
      userTargetDatabaseTable().findTargetSecured(user.id()).thenCompose(target ->
        findWebhookOwner(user, target).thenCompose(owner ->
          webhookDatabaseTable().generateAvailableWebhookId().thenCompose(id ->
            checkWebhookNumberLimit(user, target).thenCompose(limitReached ->
              addWebhook(user, owner, body.getSanitizedString("name", 64), id,
                limitReached, response))))));
  }

  private CompletableFuture<UUID> findWebhookOwner(User user, UUID target) {
    return user.id().equals(target) ?
      CompletableFuture.completedFuture(target) :
      teamTargetDatabaseTable().findTargetSecured(user.id())
        .thenApply(team -> team.orElse(target));
  }

  private CompletableFuture<Boolean> checkWebhookNumberLimit(User user, UUID target) {
    return findOwnersOfTarget(user, target)
      .thenCompose(owners -> AsyncIterator.execute(owners, owner ->
          webhookDatabaseTable().findWebhookCount(owner))
        .thenApply(sizes -> sizes.stream().mapToLong(Long::longValue).sum())
        .thenCompose(number -> bundleDatabaseTable.findBundle(target)
          .thenApply(bundle ->  bundle.webhookNumberLimit() > 0 &&
            number >= bundle.webhookNumberLimit())));
  }

  private CompletableFuture<List<UUID>> findOwnersOfTarget(User user, UUID target) {
    return user.id().equals(target) ?
      CompletableFuture.completedFuture(Lists.newArrayList(target)) :
      teamDatabaseTable.findTeamsByOrganization(target).thenApply(teams ->
        Stream.concat(teams.stream().map(Team::id).toList().stream(),
          Stream.of(target)).toList());
  }

  private CompletableFuture<Map<String, Object>> addWebhook(
    User creator, UUID ownerId, String name, String webhookId,
    boolean limitReached, HttpServletResponse response
  ) {
    if (limitReached) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return CompletableFuture.completedFuture(Maps.newHashMap());
    }
    var created = System.currentTimeMillis();
    return webhookDatabaseTable().insertWebhook(ownerId, webhookId, creator.id(),
        created, name, 0, createWebhookKey())
      .thenApply(value -> Map.of("webhook", webhookId));
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
    workflowModule.triggerWorkflows("webhook", "webhook-trigger",
      DatabaseCondition.of("webhook", webhook.id()), information, false);
    webhookDatabaseTable().useWebhook(webhook);
  }

  @RequestMapping(path = "/webhook/rename/", method = RequestMethod.POST)
  public void renameWebhook(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    performWebhookOperation(findUserId(request), body.getString("webhook"),
      webhook -> webhookDatabaseTable().renameWebhook(webhook,
        body.getSanitizedString("name", 64)), () -> {});
  }

  @RequestMapping(path = "/webhook/key/regenerate/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> regenerateWebhookKey(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
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
    var body = DulnoRequestBody.of(payload, response);
    performWebhookOperation(findUserId(request), body.getString("webhook"),
      webhook -> webhookDatabaseTable().deleteWebhook(webhook.id()), () -> {});
  }
}
