package net.taskwolf.webhook.structure;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.taskwolf.core.database.DatabaseRow;

import java.util.UUID;

@Getter
@Accessors(fluent = true)
@AllArgsConstructor(staticName = "create")
public class Webhook {
  public static Webhook of(DatabaseRow row) {
    return create(row.findCell(0).stringValue(), row.findCell(1).uuidValue(),
      row.findCell(2).uuidValue(), row.findCell(3).longValue(),
      row.findCell(4).stringValue(), row.findCell(5).longValue(),
      row.findCell(6).stringValue());
  }

  private final String id;
  private final UUID creatorId;
  private final UUID ownerId;
  private final long created;
  private String name;
  private long usages;
  private String key;

  public void use() {
    usages += 1;
  }

  public void rename(String name) {
    this.name = name;
  }

  public void changeKey(String key) {
    this.key = key;
  }
}
