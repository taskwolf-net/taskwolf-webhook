package net.taskwolf.webhook.structure;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import net.taskwolf.core.database.DatabaseRow;

import java.util.UUID;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "create")
public class Webhook {
  public static Webhook of(DatabaseRow row) {
    return create(row.findCell(0).stringValue(), row.findCell(1).uuidValue(),
      row.findCell(2).uuidValue(), row.findCell(3).longValue(),
      row.findCell(4).longValue());
  }

  private final String id;
  private final UUID creatorId;
  private final UUID ownerId;
  private final long created;
  private final long usages;
}
