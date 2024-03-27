package net.taskwolf.webhook.trigger;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import net.taskwolf.core.trigger.Trigger;
import net.taskwolf.core.trigger.TriggerInformation;
import net.taskwolf.core.workflow.component.input.InputComponentSelect;
import net.taskwolf.core.workflow.component.input.InputComponentVariable;
import net.taskwolf.core.workflow.component.output.OutputComponentVariable;
import org.json.JSONObject;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "create")
public final class WebhookTrigger implements Trigger {
  public static TriggerInformation information(
    InputComponentSelect webhookComponentSelect
  ) {
    return TriggerInformation.builder()
      .withName("webhook.trigger.name")
      .withDescription("webhook.trigger.description")
      .withIdentifier("webhook-trigger")
      .withInputVariable(InputComponentVariable.createSelect("webhook.trigger.input.webhook.name",
        "webhookIdentifier", "webhook.trigger.input.webhook.description", webhookComponentSelect))
      .withOutputVariable(OutputComponentVariable.create("webhook.trigger.output.webhook.id", "webhookId"))
      .withOutputVariable(OutputComponentVariable.create("webhook.trigger.output.webhook.url", "webhookUrl"))
      .build();
  }

  public static WebhookTrigger of(JSONObject content) {
    return create(content.getString("webhookIdentifier"));
  }

  private final String webhookIdentifier;
}
