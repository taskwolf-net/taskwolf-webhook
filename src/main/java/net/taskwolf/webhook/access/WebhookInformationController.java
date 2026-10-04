package net.taskwolf.webhook.access;

import net.taskwolf.core.environment.TaskwolfEnvironment;
import net.taskwolf.webhook.structure.Webhook;
import net.taskwolf.webhook.structure.WebhookDatabaseTable;
import net.taskwolf.webhook.structure.WebhookURL;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.database.paging.DatabaseDirection;
import net.taskwolf.core.database.paging.DatabaseOrder;
import net.taskwolf.core.database.paging.DatabasePage;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.organization.team.TeamTargetDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
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
  private final TaskwolfEnvironment environment;
  private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy");

  private WebhookInformationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    WebhookDatabaseTable webhookDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable,
    TaskwolfEnvironment environment
  ) {
    super(secretKey, userDatabaseTable, webhookDatabaseTable,
      userTargetDatabaseTable, teamTargetDatabaseTable);
    this.environment = environment;
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

  @RequestMapping(path = "/webhooks/page/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findWebhookPage(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var targetPage = body.getInt("targetPage");
    var sortingColumn = body.getString("sorting");
    var sortingOrder = DatabaseOrder.valueOf(body.getString("order"));
    var search = body.getString("search");
    var creatorId = body.has("creator") ? body.getUUID("creator") : null;
    var startTime = body.has("startTime") ? body.getLong("startTime") : -1;
    var endTime = body.has("endTime") ? body.getLong("endTime") : -1;
    var minimumUsages = body.has("minimumUsages") ? body.getLong("minimumUsages") : -1;
    var maximumUsages = body.has("maximumUsages") ? body.getLong("maximumUsages") : -1;
    return findWebhookTarget(findUserId(request)).thenCompose(target ->
      webhookDatabaseTable().findWebhooksOfOwner(target, targetPage,
          sortingColumn, sortingOrder, search, creatorId, startTime, endTime,
          minimumUsages, maximumUsages)
        .thenCompose(this::collectWebhookInformation));
  }

  @RequestMapping(path = "/webhooks/page/shift/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> shiftWebhookPage(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var pageState = body.getString("pageState");
    var startingPoint = DatabaseDirection.valueOf(body.getString("startingPoint"));
    var direction = DatabaseDirection.valueOf(body.getString("direction"));
    var sortingColumn = body.getString("sorting");
    var sortingOrder = DatabaseOrder.valueOf(body.getString("order"));
    var creatorId = body.has("creator") ? body.getUUID("creator") : null;
    var startTime = body.has("startTime") ? body.getLong("startTime") : -1;
    var endTime = body.has("endTime") ? body.getLong("endTime") : -1;
    var minimumUsages = body.has("minimumUsages") ? body.getLong("minimumUsages") : -1;
    var maximumUsages = body.has("maximumUsages") ? body.getLong("maximumUsages") : -1;
    return findWebhookTarget(findUserId(request)).thenCompose(target ->
      webhookDatabaseTable().findWebhooksOfOwner(target, pageState,
          startingPoint, direction, sortingColumn, sortingOrder, creatorId,
          startTime, endTime, minimumUsages, maximumUsages)
        .thenCompose(this::collectWebhookInformation));
  }

  private CompletableFuture<Map<String, Object>> collectWebhookInformation(
    DatabasePage<Webhook> page
  ) {
    if (page.content().isEmpty()) {
      return CompletableFuture.completedFuture(Map.of("webhooks",
        Lists.newArrayList(), "page", page.pageState(), "pageNumber", 0));
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    AsyncIterator.execute(page.content(), this::gatherWebhookInformation)
      .thenApply(information -> reconstructWebhookOrder(page, information))
      .thenAccept(information -> futureResponse.complete(Map.of("webhooks",
        information, "page", page.pageState(), "pageNumber", page.pageNumber())));
    return futureResponse;
  }

  private List<Map<String, Object>> reconstructWebhookOrder(
    DatabasePage<Webhook> page, List<Map<String, Object>> information
  ) {
    var result = Lists.<Map<String, Object>>newArrayList();
    for (var webhook : page.content()) {
      for (var entry : information) {
        if (webhook.id().toString().equals(entry.get("id").toString())) {
          result.add(entry);
          break;
        }
      }
    }
    return result;
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
    information.put("url", WebhookURL.create(environment, webhook).build());
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

