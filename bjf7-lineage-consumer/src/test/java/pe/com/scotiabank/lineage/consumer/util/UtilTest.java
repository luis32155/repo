package pe.com.scotiabank.lineage.consumer.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pe.com.scotiabank.lineage.consumer.support.AuditEventFixtures.event;
import static pe.com.scotiabank.lineage.consumer.support.AuditEventFixtures.eventWithMessage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import pe.com.scotiabank.lineage.common.domain.AuditLogs;

class UtilTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final Util util = new Util(mapper);

  @ParameterizedTest
  @CsvSource({"DNI,1", "CE,2", "PASSPORT,7", "RUC,5", "DOC_PYRL,14", "MIN,10", "OTHERS,99"})
  void mapsSupportedDocumentTypes(String type, String expected) {
    assertEquals(expected, util.getDocumentType(type));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "UNKNOWN", "dni"})
  void rejectsUnknownDocumentTypes(String type) {
    assertNull(util.getDocumentType(type));
  }

  @Test
  void extractsSqlFieldsAndPreservesDynamicMessageContent() throws Exception {
    AuditLogs audit = event("TAPP").getPayload().getAuditLogs();
    Set<String> applications = new HashSet<>();
    Set<String> journeys = new HashSet<>();
    List<AuditLogs> accepted = new ArrayList<>();

    util.initialize(audit, applications, journeys, accepted);

    assertEquals(List.of(audit), accepted);
    assertEquals(Set.of("TAPP"), applications);
    assertEquals(Set.of("LOGIN"), journeys);
    assertEquals("TAPP", audit.getAppCode());
    assertEquals("LOGIN", audit.getAppOperationCode());
    assertEquals("1", audit.getTypeId());
    assertEquals("12345678", audit.getNumberId());
    assertEquals("local-device", audit.getDeviceId());
    assertEquals(
        mapper.readTree("{\"operation_number\":\"123\"}"), mapper.readTree(audit.getMessage()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{broken", "{}", "null"})
  void skipsMalformedOrEmptyMessages(String message) {
    AuditLogs audit = eventWithMessage("invalid", message).getPayload().getAuditLogs();
    Set<String> applications = new HashSet<>();
    Set<String> journeys = new HashSet<>();
    List<AuditLogs> accepted = new ArrayList<>();

    util.initialize(audit, applications, journeys, accepted);

    assertTrue(accepted.isEmpty());
    assertTrue(applications.isEmpty());
    assertTrue(journeys.isEmpty());
    assertEquals(message, audit.getMessage());
  }

  @Test
  void skipsUnsupportedDocumentBeforeChangingSqlFields() {
    AuditLogs audit = event("TAPP").getPayload().getAuditLogs();
    audit.setMessage(audit.getMessage().replace("\"DNI\"", "\"UNKNOWN\""));
    List<AuditLogs> accepted = new ArrayList<>();

    util.initialize(audit, new HashSet<>(), new HashSet<>(), accepted);

    assertTrue(accepted.isEmpty());
    assertNull(audit.getTypeId());
    assertNull(audit.getAppCode());
  }

  @Test
  void doesNotAcceptAuditWhenSimplifiedMessageCannotBeSerialized() throws Exception {
    ObjectMapper failingMapper = mock(ObjectMapper.class);
    AuditLogs audit = event("TAPP").getPayload().getAuditLogs();
    Map<String, Object> message = mapper.readValue(audit.getMessage(), Map.class);
    when(failingMapper.readValue(audit.getMessage(), Map.class)).thenReturn(message);
    when(failingMapper.writeValueAsString(any()))
        .thenThrow(new JsonProcessingException("failure") {});
    Set<String> applications = new HashSet<>();
    Set<String> journeys = new HashSet<>();
    List<AuditLogs> accepted = new ArrayList<>();

    new Util(failingMapper).initialize(audit, applications, journeys, accepted);

    assertTrue(accepted.isEmpty());
    assertTrue(applications.isEmpty());
    assertTrue(journeys.isEmpty());
  }

  @Test
  void preservesNestedValuesWhenSimplifyingMessage() throws Exception {
    Map<String, Object> content =
        Map.of(
            "operation_number", "123", "details", List.of(Map.of("amount", 150, "approved", true)));

    String result = util.getSimplifiedEventMessage(content);

    assertEquals(mapper.valueToTree(content), mapper.readTree(result));
  }

  @Test
  void returnsNullWhenMessageCannotBeParsed() throws Exception {
    ObjectMapper failingMapper = mock(ObjectMapper.class);
    AuditLogs audit = event("TAPP").getPayload().getAuditLogs();
    when(failingMapper.readValue(eq(audit.getMessage()), eq(Map.class)))
        .thenThrow(new JsonProcessingException("invalid") {});

    assertNull(new Util(failingMapper).getMapMessage(audit));
  }

  @Test
  void keepsOnlyRegisteredChannelsAndAssignsTheirIds() {
    AuditLogs first = new AuditLogs();
    first.setAppCode("TAPP");
    first.setAppOperationCode("LOGIN");
    AuditLogs missing = new AuditLogs();
    missing.setAppCode("OTHER");
    missing.setAppOperationCode("UNKNOWN");
    AuditLogs second = new AuditLogs();
    second.setAppCode("TAPP");
    second.setAppOperationCode("PAYMENT");

    List<AuditLogs> result =
        util.filterAndInitByJourneyId(
            List.of(first, missing, second), Map.of("TAPP-LOGIN", 1, "TAPP-PAYMENT", 2));

    assertEquals(List.of(first, second), result);
    assertSame(first, result.get(0));
    assertEquals(1, first.getJourneyId());
    assertEquals(2, second.getJourneyId());
    assertNull(missing.getJourneyId());
    assertFalse(result.contains(missing));
  }

  @Test
  void emptyChannelCatalogDoesNotAcceptAnyAudit() {
    assertTrue(util.filterAndInitByJourneyId(List.of(new AuditLogs()), new HashMap<>()).isEmpty());
    assertTrue(util.filterAndInitByJourneyId(List.of(), Map.of("TAPP-LOGIN", 1)).isEmpty());
  }
}
