package com.dulno.webhook;

import com.dulno.webhook.select.WebhookMethodComponentSelect;
import com.dulno.webhook.structure.WebhookDatabaseTable;
import com.dulno.webhook.trigger.WebhookTrigger;
import com.dulno.workflow.integration.Integration;
import com.google.inject.Injector;
import com.dulno.core.account.AccountLink;
import com.dulno.workflow.action.ActionRepository;
import com.dulno.core.database.DatabaseConnection;
import com.dulno.core.database.DatabaseKeyspace;
import com.dulno.core.log.Log;
import com.dulno.core.module.Module;
import com.dulno.core.module.ModuleDescription;
import com.dulno.core.module.ModuleInformation;
import com.dulno.core.module.ModuleLoadPriority;
import com.dulno.workflow.trigger.TriggerRepository;
import com.dulno.workflow.component.input.InputComponentSelect;
import com.dulno.webhook.action.WebhookAction;
import com.dulno.webhook.select.WebhookComponentSelect;
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