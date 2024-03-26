package net.taskwolf.webhook;

import com.google.common.collect.Lists;
import com.google.inject.Injector;
import net.taskwolf.core.account.AccountLink;
import net.taskwolf.core.action.ActionFactory;
import net.taskwolf.core.action.ActionInformation;
import net.taskwolf.core.log.Log;
import net.taskwolf.core.module.Module;
import net.taskwolf.core.module.ModuleDescription;
import net.taskwolf.core.module.ModuleInformation;
import net.taskwolf.core.module.ModuleLoadPriority;
import net.taskwolf.core.trigger.TriggerFactory;
import net.taskwolf.core.trigger.TriggerInformation;
import net.taskwolf.webhook.trigger.WebhookTriggerFactory;
import org.springframework.boot.SpringApplication;

import java.util.List;

@ModuleDescription(name = "webhook", version = "1.0.0-SNAPSHOT",
  priority = ModuleLoadPriority.NEUTRAL)
public final class WebhookModule extends Module {
  private Log log;
  private TriggerFactory triggerFactory;
  private AccountLink accountLink;

  public WebhookModule(Injector injector) {
    super(injector.createChildInjector(WebhookInjectionModule.create()));
  }

  @Override
  public void enable() throws Exception {
    log = injector().getInstance(Log.class).subLog("Webhook");
    injector().getInstance(SpringApplication.class).addInitializers(
      injector().getInstance(WebhookContextInitializer.class));
    triggerFactory = WebhookTriggerFactory.create();
    accountLink = WebhookAccountLink.create();
  }

  @Override
  public void disable() {

  }

  @Override
  public TriggerFactory triggerFactory() {
    return triggerFactory;
  }

  @Override
  public ActionFactory actionFactory() {
    return null;
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
  public List<TriggerInformation> triggerInformation() {
    return Lists.newArrayList();
  }

  @Override
  public List<ActionInformation> actionInformation() {
    return Lists.newArrayList();
  }
}