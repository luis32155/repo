package pe.com.scotiabank.lineage.consumer.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static pe.com.scotiabank.lineage.consumer.support.AuditEventFixtures.event;
import static pe.com.scotiabank.lineage.consumer.support.AuditEventFixtures.eventWithMessage;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import pe.com.scotiabank.lineage.common.domain.AuditLogs;
import pe.com.scotiabank.lineage.common.domain.QueueContract;
import pe.com.scotiabank.lineage.consumer.application.ports.output.DatabaseOutPort;
import pe.com.scotiabank.lineage.consumer.application.ports.output.MongoAuditOutPort;
import pe.com.scotiabank.lineage.consumer.util.Util;

class ConsumerApplicationImplTest {

  private final DatabaseOutPort sql = mock(DatabaseOutPort.class);
  private final MongoAuditOutPort mongo = mock(MongoAuditOutPort.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private final ConsumerApplicationImpl service =
      new ConsumerApplicationImpl(
          sql, new Util(mapper), new MongoAuditService(mongo, mapper, "TAPP"));

  @Test
  void writesTappToMongoThenKeepsExistingSqlFlowForWholeBatch() throws Exception {
    QueueContract tapp = event("TAPP");
    QueueContract other = event("OTHER");
    final String originalMessage = tapp.getPayload().getAuditLogs().getMessage();
    when(sql.getChannelIdsByCodes(Set.of("TAPP", "OTHER"), Set.of("LOGIN")))
        .thenReturn(Map.of("TAPP-LOGIN", 1, "OTHER-LOGIN", 2));

    service.process(List.of(tapp, other));

    InOrder order = inOrder(mongo, sql);
    order.verify(mongo).save(List.of(tapp));
    order.verify(sql).getChannelIdsByCodes(Set.of("TAPP", "OTHER"), Set.of("LOGIN"));
    order.verify(sql).initIds(anyList());
    ArgumentCaptor<List<AuditLogs>> saved = ArgumentCaptor.forClass(List.class);
    order.verify(sql).save(saved.capture());
    assertEquals(2, saved.getValue().size());
    assertEquals("TAPP", saved.getValue().get(0).getAppCode());
    assertEquals(1, saved.getValue().get(0).getJourneyId());
    assertFalse(mapper.readTree(saved.getValue().get(0).getMessage()).has("app_code"));
    assertEquals(originalMessage, tapp.getPayload().getAuditLogs().getMessage());
  }

  @Test
  void otherApplicationsContinueOnlyToSql() {
    when(sql.getChannelIdsByCodes(Set.of("OTHER"), Set.of("LOGIN")))
        .thenReturn(Map.of("OTHER-LOGIN", 2));

    service.process(List.of(event("OTHER")));

    verifyNoInteractions(mongo);
    verify(sql).save(anyList());
  }

  @Test
  void keepsMongoCopyEvenWhenSqlChannelIsNotRegistered() {
    QueueContract tapp = event("TAPP");
    when(sql.getChannelIdsByCodes(Set.of("TAPP"), Set.of("LOGIN"))).thenReturn(Map.of());

    service.process(List.of(tapp));

    verify(mongo).save(List.of(tapp));
    verify(sql, never()).initIds(anyList());
    verify(sql, never()).save(anyList());
  }

  @Test
  void keepsMongoCopyEvenWhenSqlRejectsDocumentType() {
    QueueContract tapp = event("TAPP");
    QueueContract unsupported =
        eventWithMessage(
            "unsupported",
            tapp.getPayload().getAuditLogs().getMessage().replace("DNI", "UNSUPPORTED"));

    service.process(List.of(unsupported));

    verify(mongo).save(List.of(unsupported));
    verifyNoInteractions(sql);
  }

  @Test
  void mongoFailurePropagatesBeforeSqlCanWrite() {
    QueueContract tapp = event("TAPP");
    doThrow(new IllegalStateException("MongoDB unavailable")).when(mongo).save(List.of(tapp));

    assertThrows(IllegalStateException.class, () -> service.process(List.of(tapp)));

    verifyNoInteractions(sql);
  }

  @Test
  void skipsEmptyAndIncompleteBatchesWithoutQueryingSql() {
    service.process(List.of());
    service.process(Arrays.asList(null, new QueueContract()));
    service.process(List.of(eventWithMessage("invalid", "invalid json")));

    verifyNoInteractions(mongo, sql);
  }
}
