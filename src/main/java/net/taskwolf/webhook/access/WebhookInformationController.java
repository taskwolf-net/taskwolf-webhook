package net.taskwolf.webhook.access;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.organization.team.TeamTargetDatabaseTable;
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
import java.util.concurrent.CompletableFuture;

@RestController
public final class WebhookInformationController extends WebhookController {
  private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy");

  private WebhookInformationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    WebhookDatabaseTable webhookDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, webhookDatabaseTable,
      userTargetDatabaseTable, teamTargetDatabaseTable);
  }

  @RequestMapping(path = "/webhook/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findWebhook(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> performWebhookOperation(user,
      body.getString("webhook"), webhook -> gatherWebhookInformation(webhook)
        .thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Maps.newHashMap())));
    return futureResponse;
  }

  @RequestMapping(path = "/webhooks/selected/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> selectedWebhooks(
    HttpServletRequest request
  ) {
    return findUser(request).thenCompose(user -> findViewableWebhooks(user.id())
      .thenCompose(this::collectWebhooksInformation));
  }

  private CompletableFuture<Map<String, Object>> collectWebhooksInformation(
    List<Webhook> webhooks
  ) {
    if (webhooks.isEmpty()) {
      return CompletableFuture.completedFuture(Map.of("webhooks",
        Lists.newArrayList()));
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    AsyncIterator.execute(webhooks, this::gatherWebhookInformation).thenAccept(
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
}

