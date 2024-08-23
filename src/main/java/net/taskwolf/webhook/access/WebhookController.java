package net.taskwolf.webhook.access;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.organization.team.TeamTargetDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import net.taskwolf.webhook.structure.Webhook;
import net.taskwolf.webhook.structure.WebhookDatabaseTable;

import java.security.Key;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Accessors(fluent = true)
@Getter(AccessLevel.PROTECTED)
public class WebhookController extends TaskwolfRestController {
  private final WebhookDatabaseTable webhookDatabaseTable;
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final TeamTargetDatabaseTable teamTargetDatabaseTable;

  protected WebhookController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    WebhookDatabaseTable webhookDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.webhookDatabaseTable = webhookDatabaseTable;
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.teamTargetDatabaseTable = teamTargetDatabaseTable;
  }

  protected void performWebhookOperation(
    UUID userId, String webhookId, Consumer<Webhook> operation,
    Runnable failResponse
  ) {
    userDatabaseTable().findUser(userId).thenAccept(user ->
      performWebhookOperation(user, webhookId, operation, failResponse));
  }

  protected void performWebhookOperation(
    User user, String webhookId, Consumer<Webhook> operation,
    Runnable failResponse
  ) {
    webhookDatabaseTable.webhookExists(webhookId).thenAccept(exists ->
      performWebhookOperation(user, webhookId, exists, operation,
        failResponse));
  }

  private void performWebhookOperation(
    User user, String webhookId, boolean webhookExists,
    Consumer<Webhook> operation, Runnable failResponse
  ) {
    if (!webhookExists) {
      failResponse.run();
      return;
    }
    webhookDatabaseTable.findWebhook(webhookId).thenAccept(webhook ->
      checkWebhookAuthorization(user, webhook).thenAccept(authorized ->
        performWebhookOperation(webhook, authorized, operation, failResponse)));
  }

  private void performWebhookOperation(
    Webhook webhook, boolean authorized, Consumer<Webhook> operation,
    Runnable failResponse
  ) {
    if (!authorized) {
      failResponse.run();
      return;
    }
    operation.accept(webhook);
  }

  protected CompletableFuture<Boolean> checkWebhookAuthorization(
    User user, Webhook webhook
  ) {
    return checkWebhookAuthorization(user, webhook.ownerId());
  }

  protected CompletableFuture<Boolean> checkWebhookAuthorization(
    User user, UUID webhookOwnerId
  ) {
    if (webhookOwnerId.equals(user.id()) ||
      user.organizations().contains(webhookOwnerId)
    ) {
      return CompletableFuture.completedFuture(true);
    }
    return teamTargetDatabaseTable.findTargetSecured(user.id())
      .thenApply(teamTarget -> teamTarget.map(uuid ->
        uuid.equals(webhookOwnerId)).orElse(false));
  }

  protected CompletableFuture<UUID> findWebhookTarget(UUID userId) {
    return userTargetDatabaseTable.findTargetSecured(userId)
      .thenCompose(target -> findWebhookTarget(userId, target));
  }

  private CompletableFuture<UUID> findWebhookTarget(
    UUID userId, UUID target
  ) {
    return userId.equals(target) ? CompletableFuture.completedFuture(target) :
      teamTargetDatabaseTable.findTargetSecured(userId)
        .thenApply(team -> team.orElse(target));
  }
}
