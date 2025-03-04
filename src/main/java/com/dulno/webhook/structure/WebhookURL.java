package com.dulno.webhook.structure;

import com.dulno.core.environment.DulnoEnvironment;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(staticName = "create")
public final class WebhookURL {
  private final DulnoEnvironment environment;
  private final Webhook webhook;

  private static final String WEBHOOK_URL = "https://api.%s/v1/webhook/trigger/%s/";

  public String build() {
    return String.format(WEBHOOK_URL, environment.domain(), webhook.id());
  }
}
