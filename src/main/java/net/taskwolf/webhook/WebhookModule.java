package net.taskwolf.webhook;

import net.taskwolf.webhook.select.WebhookMethodComponentSelect;
import net.taskwolf.webhook.structure.WebhookDatabaseTable;
import net.taskwolf.webhook.trigger.WebhookTrigger;
import net.taskwolf.workflow.integration.Integration;
import com.google.inject.Injector;
import net.taskwolf.core.account.AccountLink;
import net.taskwolf.workflow.action.ActionRepository;
import net.taskwolf.core.database.DatabaseConnection;
import net.taskwolf.core.database.DatabaseKeyspace;
import net.taskwolf.core.log.Log;
import net.taskwolf.core.module.Module;
import net.taskwolf.core.module.ModuleDescription;
import net.taskwolf.core.module.ModuleInformation;
import net.taskwolf.core.module.ModuleLoadPriority;
import net.taskwolf.workflow.trigger.TriggerRepository;
import net.taskwolf.workflow.component.input.InputComponentSelect;
import net.taskwolf.webhook.action.WebhookAction;
import net.taskwolf.webhook.select.WebhookComponentSelect;
import org.springframework.boot.SpringApplication;

@ModuleDescription(name = "webhook", version = "1.0.0-SNAPSHOT",
  priority = ModuleLoadPriority.NEUTRAL)
public final class WebhookModule extends Integration {
  private Log log;
  private AccountLink accountLink;
  private InputComponentSelect webhookComponentSelect;
  private InputComponentSelect webhookMethodComponentSelect;

  public WebhookModule(Injector injector) {
    super(injector.createChildInjector(WebhookInjectionModule.create()));
  }

  @Override
  public void enable() throws Exception {
    log = injector().getInstance(Log.class).subLog("Webhook");
    injector().getInstance(SpringApplication.class).addInitializers(
      injector().getInstance(WebhookContextInitializer.class));
    var webhookDatabaseTable = injector().getInstance(WebhookDatabaseTable.class);
    accountLink = WebhookAccountLink.create(webhookDatabaseTable);
    webhookComponentSelect = WebhookComponentSelect.create(webhookDatabaseTable);
    webhookMethodComponentSelect = WebhookMethodComponentSelect.create();
  }

  @Override
  public void disable() {

  }

  @Override
  public AccountLink accountLink() {
    return accountLink;
  }

  @Override
  public ModuleInformation moduleInformation() {
    return ModuleInformation.create("Webhook", "", "webhook.png",
      ModuleInformation.Type.PUBLIC);
  }

  @Override
  public TriggerRepository triggerRepository() {
    var databaseConnection = injector().getInstance(DatabaseConnection.class);
    var databaseKeyspace = injector().getInstance(DatabaseKeyspace.class);
    var webhookDatabaseTable = injector().getInstance(WebhookDatabaseTable.class);
    var repository = TriggerRepository.create();
    repository.registerTrigger(WebhookTrigger.create(webhookDatabaseTable,
      webhookComponentSelect, databaseConnection, databaseKeyspace));
    return repository;
  }

  @Override
  public ActionRepository actionRepository() {
    var databaseConnection = injector().getInstance(DatabaseConnection.class);
    var databaseKeyspace = injector().getInstance(DatabaseKeyspace.class);
    var repository = ActionRepository.create();
    repository.registerAction(WebhookAction.create(webhookMethodComponentSelect,
      databaseConnection, databaseKeyspace));
    return repository;
  }
}