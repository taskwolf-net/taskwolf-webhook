package net.taskwolf.webhook;

import net.taskwolf.core.account.AccountLinkEntry;
import net.taskwolf.webhook.structure.WebhookDatabaseTable;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.account.AccountLink;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor(staticName = "create")
public final class WebhookAccountLink implements AccountLink {
  private final WebhookDatabaseTable webhookDatabaseTable;

  @Override
  public CompletableFuture<Boolean> accountExists(UUID id) {
    return webhookDatabaseTable.webhookExistsByOwner(id);
  }

  @Override
  public CompletableFuture<List<AccountLinkEntry>> findAccounts(UUID userId) {
    return CompletableFuture.completedFuture(Lists.newArrayList());
  }

  @Override
  public void removeAccount(UUID userId, String identifier) {

  }

  @Override
  public String registrationUrl(UUID id, String apiKey) {
    return "/webhook/add/";
  }

  @Override
  public String description() {
    return "webhook.link.description";
  }
}