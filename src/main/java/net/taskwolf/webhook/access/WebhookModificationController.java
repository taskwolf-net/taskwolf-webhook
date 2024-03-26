package net.taskwolf.webhook.access;

import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;

@RestController
public final class WebhookModificationController extends TaskwolfRestController {
  private WebhookModificationController(
    Key secretKey, UserDatabaseTable userDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
  }
}
