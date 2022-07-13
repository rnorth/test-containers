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
        final String username = "my-name";
        final String password = "my-pass";
        try (
            final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:5.0.9"))
                .withUsername(username)
                .withPassword(password)
        ) {
            mongoDBContainer.start();
            final ConnectionString connectionString = new ConnectionString(mongoDBContainer.getReplicaSetUrl());
            try (final MongoClient mongoSyncClientFullAccess = MongoClients.create(connectionString)) {
                final MongoDatabase adminDatabase = mongoSyncClientFullAccess.getDatabase(
                    MongoDBContainer.DEFAULT_AUTHENTICATION_DATABASE_NAME
                );
                final MongoDatabase testDatabaseFullAccess = mongoSyncClientFullAccess.getDatabase(
                    MongoDBContainer.DEFAULT_DATABASE_NAME
                );
                final String collectionName = "my-collection";
                final Document document = new Document("abc", 1);
                testDatabaseFullAccess.getCollection(collectionName).insertOne(document);
                final String username1 = username + "1";
                final String password1 = password + "1";
                adminDatabase.runCommand(
                    new BasicDBObject("createUser", username1)
                        .append("pwd", password1)
                        .append(
                            "roles",
                            Collections.singletonList(
                                new BasicDBObject("role", "read").append("db", MongoDBContainer.DEFAULT_DATABASE_NAME)
                            )
                        )
                );
                try (
                    final MongoClient mongoSyncRestrictedAccess = MongoClients.create(
                        mongoDBContainer.getReplicaSetUrl(MongoDBContainer.DEFAULT_DATABASE_NAME, username1, password1)
                    )
                ) {
                    final MongoCollection<Document> collection = mongoSyncRestrictedAccess
                        .getDatabase(MongoDBContainer.DEFAULT_DATABASE_NAME)
                        .getCollection(collectionName);
                    assertEquals(collection.find().first(), document);
                    assertThrows(MongoCommandException.class, () -> collection.insertOne(new Document("abc", 2)));
                    adminDatabase.runCommand(
                        new BasicDBObject("updateUser", username1)
                            .append(
                                "roles",
                                Collections.singletonList(
                                    new BasicDBObject("role", "readWrite")
                                        .append("db", MongoDBContainer.DEFAULT_DATABASE_NAME)
                                )
                            )
                    );
                    collection.insertOne(new Document("abc", 2));
                    assertEquals(2, collection.countDocuments());
                    assertEquals(username, connectionString.getUsername());
                    assertEquals(password, new String(Objects.requireNonNull(connectionString.getPassword())));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
