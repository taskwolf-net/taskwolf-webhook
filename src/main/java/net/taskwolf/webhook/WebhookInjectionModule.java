package net.taskwolf.webhook;

import net.taskwolf.webhook.structure.WebhookDatabaseTable;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.database.DatabaseConnection;
import net.taskwolf.core.database.DatabaseKeyspace;

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
