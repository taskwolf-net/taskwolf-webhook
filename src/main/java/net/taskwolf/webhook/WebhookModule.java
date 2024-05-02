package net.taskwolf.webhook;

import com.google.inject.Injector;
import net.taskwolf.core.account.AccountLink;
import net.taskwolf.core.action.ActionRepository;
import net.taskwolf.core.database.DatabaseConnection;
import net.taskwolf.core.database.DatabaseKeyspace;
import net.taskwolf.core.log.Log;
import net.taskwolf.core.module.Module;
import net.taskwolf.core.module.ModuleDescription;
import net.taskwolf.core.module.ModuleInformation;
import net.taskwolf.core.module.ModuleLoadPriority;
import net.taskwolf.core.trigger.TriggerRepository;
import net.taskwolf.core.workflow.component.input.InputComponentSelect;
import net.taskwolf.webhook.structure.WebhookDatabaseTable;
import net.taskwolf.webhook.trigger.WebhookTrigger;
import org.springframework.boot.SpringApplication;

@ModuleDescription(name = "webhook", version = "1.0.0-SNAPSHOT",
  priority = ModuleLoadPriority.NEUTRAL)
public final class WebhookModule extends Module {
  private Log log;
  private AccountLink accountLink;
  private InputComponentSelect webhookComponentSelect;

  public WebhookModule(Injector injector) {
    super(injector.createChildInjector(WebhookInjectionModule.create()));
  }

  @Override
  public void enable() throws Exception {
    log = injector().getInstance(Log.class).subLog("Webhook");
    injector().getInstance(SpringApplication.class).addInitializers(
      injector().getInstance(WebhookContextInitializer.class));
    accountLink = WebhookAccountLink.create();
    webhookComponentSelect = WebhookComponentSelect.create(
      injector().getInstance(WebhookDatabaseTable.class));
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
    var repository = TriggerRepository.create();
    repository.registerTrigger(WebhookTrigger.create(webhookComponentSelect,
      databaseConnection, databaseKeyspace));
    return repository;
  }

  @Override
  public ActionRepository actionRepository() {
    return ActionRepository.create();
  }
}