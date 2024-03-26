package net.taskwolf.webhook.trigger;

import lombok.RequiredArgsConstructor;
import net.taskwolf.core.trigger.Trigger;
import net.taskwolf.core.trigger.TriggerFactory;
import org.json.JSONObject;

@RequiredArgsConstructor(staticName = "create")
public class WebhookTriggerFactory implements TriggerFactory {
  @Override
  public Trigger create(String type, String content) {
    var json = new JSONObject(content);
    return null;
  }
}
