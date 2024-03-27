package net.taskwolf.webhook.structure;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(staticName = "create")
public final class WebhookURL {
  private final Webhook webhook;

  private static final String WEBHOOK_URL = "https://api.taskwolf.net/v1/webhook/trigger/%s/";

  public String build() {
    return String.format(WEBHOOK_URL, webhook.id());
  }
}
