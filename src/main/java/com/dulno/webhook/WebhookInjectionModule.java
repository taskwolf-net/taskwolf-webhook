package com.dulno.webhook;

import com.dulno.webhook.structure.WebhookDatabaseTable;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import com.dulno.core.database.DatabaseConnection;
import com.dulno.core.database.DatabaseKeyspace;

@RequiredArgsConstructor(staticName = "create")
public class WebhookInjectionModule extends AbstractModule {
  @Override
  protected void configure() {

  }

  @Provides
  @Singleton
  WebhookDatabaseTable provideWebhookDatabaseTable(
    DatabaseConnection connection, DatabaseKeyspace keyspace
  ) {
    return WebhookDatabaseTable.create(connection, keyspace);
  }
}
