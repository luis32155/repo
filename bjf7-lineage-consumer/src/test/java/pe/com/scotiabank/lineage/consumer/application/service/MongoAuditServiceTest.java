package pe.com.scotiabank.lineage.consumer.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static pe.com.scotiabank.lineage.consumer.support.AuditEventFixtures.event;
import static pe.com.scotiabank.lineage.consumer.support.AuditEventFixtures.eventWithMessage;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.com.scotiabank.lineage.common.domain.QueueContract;
import pe.com.scotiabank.lineage.consumer.application.ports.output.MongoAuditOutPort;

class MongoAuditServiceTest {

  private final MongoAuditOutPort mongoPort = mock(MongoAuditOutPort.class);
  private final MongoAuditService service =
      new MongoAuditService(mongoPort, new ObjectMapper(), "TAPP");

  @Test
  void sendsOnlyTappEventsFromMixedBatch() {
    QueueContract tapp = event("TAPP");
    service.saveTappEvents(List.of(event("OTHER"), tapp, event("OTHER-2")));

    verify(mongoPort).save(List.of(tapp));
    verifyNoMoreInteractions(mongoPort);
  }

  @Test
  void acceptsDifferentCaseAndWhitespaceWithoutChangingOriginalMessage() {
    QueueContract tapp = event(" tapp ");
    String originalMessage = tapp.getPayload().getAuditLogs().getMessage();

    service.saveTappEvents(List.of(tapp));

    verify(mongoPort).save(List.of(tapp));
    assertEquals(originalMessage, tapp.getPayload().getAuditLogs().getMessage());
  }

  @Test
  void doesNotWriteForOtherApplicationsOrEmptyBatch() {
    service.saveTappEvents(List.of(event("OTHER")));
    service.saveTappEvents(List.of());

    verifyNoInteractions(mongoPort);
  }

  @Test
  void ignoresMissingOrNonTextualAppCodesAndMalformedJson() {
    service.saveTappEvents(
        List.of(
            eventWithMessage("one", "{}"),
            eventWithMessage("two", "{\"app_code\":null}"),
            eventWithMessage("three", "{\"app_code\":123}"),
            eventWithMessage("four", "invalid json"),
            eventWithMessage("five", "null"),
            eventWithMessage("six", "[]")));

    verifyNoInteractions(mongoPort);
  }

  @Test
  void toleratesMissingEnvelopesAndMessage() {
    service.saveTappEvents(Arrays.asList(null, new QueueContract(), eventWithMessage("one", null)));

    verifyNoInteractions(mongoPort);
  }

  @Test
  void supportsConfiguredApplicationCode() {
    MongoAuditService configured = new MongoAuditService(mongoPort, new ObjectMapper(), "CUSTOM");
    QueueContract custom = event("CUSTOM");

    configured.saveTappEvents(List.of(custom, event("TAPP")));

    verify(mongoPort).save(List.of(custom));
  }
}
