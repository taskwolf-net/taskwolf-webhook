package com.dulno.webhook;

import com.dulno.webhook.structure.WebhookDatabaseTable;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public final class WebhookContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final WebhookDatabaseTable webhookDatabaseTable;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("webhookDatabaseTable", webhookDatabaseTable);
  }
}
