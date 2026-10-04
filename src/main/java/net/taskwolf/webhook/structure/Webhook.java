package net.taskwolf.webhook.structure;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.taskwolf.core.database.DatabaseColumn;
import net.taskwolf.core.database.DatabaseRow;
import net.taskwolf.core.database.DatabaseTable;

import java.util.List;
import java.util.UUID;

@Getter
@Accessors(fluent = true)
@AllArgsConstructor(staticName = "create")
public class Webhook {
  public static Webhook of(DatabaseRow row, DatabaseTable table) {
    return of(row, table.columns().stream().map(DatabaseColumn::name).toList());
  }

  public static Webhook of(DatabaseRow row, List<String> columns) {
    return create(row.findCell(columns.indexOf("owner")).uuidValue(),
      row.findCell(columns.indexOf("id")).stringValue(),
      row.findCell(columns.indexOf("creator")).uuidValue(),
      row.findCell(columns.indexOf("created")).longValue(),
      row.findCell(columns.indexOf("name")).stringValue(),
      row.findCell(columns.indexOf("usages")).longValue(),
      row.findCell(columns.indexOf("key")).stringValue());
  }

  private final UUID ownerId;
  private final String id;
  private final UUID creatorId;
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
