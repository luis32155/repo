package pe.com.scotiabank.lineage.consumer.adapters.repository;

import java.util.List;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import pe.com.scotiabank.lineage.common.domain.QueueContract;
import pe.com.scotiabank.lineage.consumer.adapters.config.MongoAuditProperties;
import pe.com.scotiabank.lineage.consumer.adapters.repository.mapper.MongoAuditDocumentMapper;
import pe.com.scotiabank.lineage.consumer.application.ports.output.MongoAuditOutPort;

@Repository
public class MongoDbRepositoryAdapter implements MongoAuditOutPort {

  private final MongoOperations mongoOperations;
  private final MongoAuditDocumentMapper documentMapper;
  private final String collection;
  private volatile boolean uidIndexReady;

  public MongoDbRepositoryAdapter(
      MongoOperations mongoOperations,
      MongoAuditDocumentMapper documentMapper,
      MongoAuditProperties properties) {
    this.mongoOperations = mongoOperations;
    this.documentMapper = documentMapper;
    this.collection = properties.getCollection();
  }

  @Override
  public void save(List<QueueContract> items) {
    if (items.isEmpty()) {
      return;
    }

    List<Document> documents = items.stream().map(documentMapper::toDocument).toList();
    ensureUidIndex();
    BulkOperations operations =
        mongoOperations.bulkOps(BulkOperations.BulkMode.ORDERED, collection);
    for (Document document : documents) {
      Query query = Query.query(Criteria.where("uid").is(document.getString("uid")));
      Update update = new Update();
      document.forEach(update::setOnInsert);
      operations.upsert(query, update);
    }
    operations.execute();
  }

  private void ensureUidIndex() {
    if (uidIndexReady) {
      return;
    }
    synchronized (this) {
      if (!uidIndexReady) {
        mongoOperations
            .indexOps(collection)
            .createIndex(
                new Index().on("uid", Sort.Direction.ASC).unique().sparse().named("uid_unique"));
        uidIndexReady = true;
      }
    }
  }
}
