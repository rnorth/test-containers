package org.testcontainers.containers;

import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.MongoCommandException;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.TransactionBody;
import org.bson.Document;
import org.junit.Test;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

public class MongoDBContainerTest {

    /**
     * Taken from <a href="https://docs.mongodb.com/manual/core/transactions/">https://docs.mongodb.com</a>
     */
    @Test
    public void shouldExecuteTransactions() {
        try (
            // creatingMongoDBContainer {
            final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.0.10"))
            // }
        ) {
            // startingMongoDBContainer {
            mongoDBContainer.start();
            // }

            final String mongoRsUrl = mongoDBContainer.getReplicaSetUrl();
            assertNotNull(mongoRsUrl);
            final String connectionString = mongoDBContainer.getConnectionString();
            final MongoClient mongoSyncClientBase = MongoClients.create(connectionString);
            final MongoClient mongoSyncClient = MongoClients.create(mongoRsUrl);
            mongoSyncClient
                .getDatabase("mydb1")
                .getCollection("foo")
                .withWriteConcern(WriteConcern.MAJORITY)
                .insertOne(new Document("abc", 0));
            mongoSyncClient
                .getDatabase("mydb2")
                .getCollection("bar")
                .withWriteConcern(WriteConcern.MAJORITY)
                .insertOne(new Document("xyz", 0));
            mongoSyncClientBase
                .getDatabase("mydb3")
                .getCollection("baz")
                .withWriteConcern(WriteConcern.MAJORITY)
                .insertOne(new Document("def", 0));

            final ClientSession clientSession = mongoSyncClient.startSession();
            final TransactionOptions txnOptions = TransactionOptions
                .builder()
                .readPreference(ReadPreference.primary())
                .readConcern(ReadConcern.LOCAL)
                .writeConcern(WriteConcern.MAJORITY)
                .build();

            final String trxResult = "Inserted into collections in different databases";

            TransactionBody<String> txnBody = () -> {
                final MongoCollection<Document> coll1 = mongoSyncClient.getDatabase("mydb1").getCollection("foo");
                final MongoCollection<Document> coll2 = mongoSyncClient.getDatabase("mydb2").getCollection("bar");

                coll1.insertOne(clientSession, new Document("abc", 1));
                coll2.insertOne(clientSession, new Document("xyz", 999));
                return trxResult;
            };

            try {
                final String trxResultActual = clientSession.withTransaction(txnBody, txnOptions);
                assertEquals(trxResult, trxResultActual);
            } catch (RuntimeException re) {
                throw new IllegalStateException(re.getMessage(), re);
            } finally {
                clientSession.close();
                mongoSyncClient.close();
            }
        }
    }

    @Test
    public void supportsMongoDB_4_4() {
        try (final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.4"))) {
            mongoDBContainer.start();
        }
    }

    @Test
    public void shouldTestDatabaseName() {
        try (final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.0.10"))) {
            mongoDBContainer.start();
            final String databaseName = "my-db";
            assertEquals(
                databaseName,
                new ConnectionString(mongoDBContainer.getReplicaSetUrl(databaseName)).getDatabase()
            );
        }
    }

    @Test
    public void shouldTestAuthentication() {
        final String usernameFullAccess = "my-name";
        final String passwordFullAccess = "my-pass";
        try (
            final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.4"))
                .withUsername(usernameFullAccess)
                .withPassword(passwordFullAccess)
        ) {
            mongoDBContainer.start();
            final ConnectionString connectionStringFullAccess = new ConnectionString(
                mongoDBContainer.getReplicaSetUrl()
            );
            try (final MongoClient mongoSyncClientFullAccess = MongoClients.create(connectionStringFullAccess)) {
                final MongoDatabase adminDatabase = mongoSyncClientFullAccess.getDatabase(
                    MongoDBContainer.DEFAULT_AUTHENTICATION_DATABASE_NAME
                );
                final MongoDatabase testDatabaseFullAccess = mongoSyncClientFullAccess.getDatabase(
                    MongoDBContainer.DEFAULT_DATABASE_NAME
                );
                final String collectionName = "my-collection";
                final Document document = new Document("abc", 1);
                testDatabaseFullAccess.getCollection(collectionName).insertOne(document);
                final String usernameRestrictedAccess = usernameFullAccess + "-restricted";
                final String passwordRestrictedAccess = passwordFullAccess + "-restricted";
                runCommand(
                    adminDatabase,
                    new BasicDBObject("createUser", usernameRestrictedAccess).append("pwd", passwordRestrictedAccess),
                    "read"
                );
                try (
                    final MongoClient mongoSyncRestrictedAccess = MongoClients.create(
                        mongoDBContainer.getReplicaSetUrl(
                            MongoDBContainer.ConnectionString
                                .builder()
                                .username(usernameRestrictedAccess)
                                .password(passwordRestrictedAccess)
                                .build()
                        )
                    )
                ) {
                    final MongoCollection<Document> collection = mongoSyncRestrictedAccess
                        .getDatabase(MongoDBContainer.DEFAULT_DATABASE_NAME)
                        .getCollection(collectionName);
                    assertEquals(collection.find().first(), document);
                    assertThrows(MongoCommandException.class, () -> collection.insertOne(new Document("abc", 2)));
                    runCommand(adminDatabase, new BasicDBObject("updateUser", usernameRestrictedAccess), "readWrite");
                    collection.insertOne(new Document("abc", 2));
                    assertEquals(2, collection.countDocuments());
                    assertEquals(usernameFullAccess, connectionStringFullAccess.getUsername());
                    assertEquals(
                        passwordFullAccess,
                        new String(Objects.requireNonNull(connectionStringFullAccess.getPassword()))
                    );
                }
            }
        }
    }

    private void runCommand(MongoDatabase adminDatabase, BasicDBObject command, String role) {
        adminDatabase.runCommand(
            command.append(
                "roles",
                Collections.singletonList(
                    new BasicDBObject("role", role).append("db", MongoDBContainer.DEFAULT_DATABASE_NAME)
                )
            )
        );
    }
}
