package pe.com.scotiabank.lineage.consumer.adapters.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static pe.com.scotiabank.lineage.consumer.support.AuditEventFixtures.event;
import static pe.com.scotiabank.lineage.consumer.support.AuditEventFixtures.eventWithMessage;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import pe.com.scotiabank.lineage.common.domain.QueueContract;
import pe.com.scotiabank.lineage.consumer.adapters.config.MongoAuditProperties;
import pe.com.scotiabank.lineage.consumer.adapters.repository.mapper.MongoAuditDocumentMapper;
import pe.com.scotiabank.lineage.consumer.domain.error.ConsumerException;

class MongoDbRepositoryAdapterTest {

  private final MongoOperations mongo = mock(MongoOperations.class);
  private final BulkOperations bulk = mock(BulkOperations.class);
  private final IndexOperations indexes = mock(IndexOperations.class);
  private MongoDbRepositoryAdapter adapter;

  @BeforeEach
  void setUp() {
    MongoAuditProperties properties = new MongoAuditProperties();
    properties.setCollection("local_audit");
    adapter =
        new MongoDbRepositoryAdapter(
            mongo, new MongoAuditDocumentMapper(new ObjectMapper(), properties), properties);
    when(mongo.indexOps("local_audit")).thenReturn(indexes);
    when(mongo.bulkOps(BulkOperations.BulkMode.ORDERED, "local_audit")).thenReturn(bulk);
  }

  @Test
  void writesFlattenedDocumentAndNestedMessage() {
    QueueContract event = event("TAPP");
    adapter.save(List.of(event));

    ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
    ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
    verify(bulk).upsert(query.capture(), update.capture());
    assertEquals(new Document("uid", "event-TAPP"), query.getValue().getQueryObject());
    Document insert = update.getValue().getUpdateObject().get("$setOnInsert", Document.class);
    assertEquals("event-TAPP", insert.getString("uid"));
    assertNotNull(insert.get("_id", ObjectId.class));
    assertEquals(
        Document.parse(event.getPayload().getAuditLogs().getMessage()),
        insert.get("event_message", Document.class));
    assertNotNull(insert.getDate("event_timestamp"));
    assertNotNull(insert.getDate("log_timestamp"));
    assertNotNull(insert.getDate("expires_at"));
    assertFalse(insert.containsKey("metadata"));
    assertFalse(insert.containsKey("payload"));
    assertFalse(insert.containsKey("received_at"));
    verify(bulk).execute();
  }

  @Test
  void replayUsesSameIdentityAndOnlyInsertFields() {
    QueueContract event = event("TAPP");
    adapter.save(List.of(event));
    adapter.save(List.of(event));

    ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
    ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
    verify(bulk, times(2)).upsert(query.capture(), update.capture());
    assertEquals(
        query.getAllValues().get(0).getQueryObject(), query.getAllValues().get(1).getQueryObject());
    for (Update operation : update.getAllValues()) {
      assertEquals(1, operation.getUpdateObject().size());
      assertFalse(operation.getUpdateObject().containsKey("$set"));
    }
    verify(mongo).indexOps("local_audit");
  }

  @Test
  void rejectsMissingUuidBeforeSendingAnyWrite() {
    assertThrows(
        ConsumerException.class,
        () -> adapter.save(List.of(event("TAPP"), eventWithMessage(null, "{}"))));

    verifyNoInteractions(mongo, bulk, indexes);
  }

  @Test
  void propagatesDatabaseFailure() {
    DataAccessResourceFailureException failure =
        new DataAccessResourceFailureException("MongoDB unavailable");
    when(bulk.execute()).thenThrow(failure);

    assertSame(
        failure,
        assertThrows(
            DataAccessResourceFailureException.class, () -> adapter.save(List.of(event("TAPP")))));
  }

  @Test
  void doesNotCreateBulkForEmptyBatch() {
    adapter.save(List.of());

    verifyNoInteractions(mongo, bulk, indexes);
  }

  @Test
  void createsUniqueUidIndexWithoutEnablingAutomaticExpiration() {
    adapter.save(List.of(event("TAPP")));

    ArgumentCaptor<IndexDefinition> index = ArgumentCaptor.forClass(IndexDefinition.class);
    verify(indexes).createIndex(index.capture());
    assertEquals(new Document("uid", 1), index.getValue().getIndexKeys());
    assertEquals(true, index.getValue().getIndexOptions().getBoolean("unique"));
    assertEquals(true, index.getValue().getIndexOptions().getBoolean("sparse"));
    assertFalse(index.getValue().getIndexOptions().containsKey("expireAfterSeconds"));
  }

  @Test
  void stopsWritesWhenIndexCreationFailsAndRetriesIndexOnNextAttempt() {
    DataAccessResourceFailureException failure =
        new DataAccessResourceFailureException("Index creation unavailable");
    when(indexes.createIndex(org.mockito.ArgumentMatchers.any(IndexDefinition.class)))
        .thenThrow(failure)
        .thenReturn("uid_unique");

    assertThrows(
        DataAccessResourceFailureException.class, () -> adapter.save(List.of(event("TAPP"))));
    verify(bulk, never()).execute();
    adapter.save(List.of(event("TAPP")));

    verify(mongo, times(2)).indexOps("local_audit");
    verify(bulk).execute();
  }
}
