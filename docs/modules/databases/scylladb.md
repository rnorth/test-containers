# ScyllaDB

Testcontainers module for [ScyllaDB](https://hub.docker.com/r/scylladb/scylla)

## ScyllaDB's usage examples

You can start a ScyllaDB container instance from any Java application by using:

<!--codeinclude-->
[Create container](../../../modules/scylladb/src/test/java/org/testcontainers/scylladb/ScyllaDBContainerTest.java) inside_block:container
<!--/codeinclude-->

<!--codeinclude-->
[Custom config file](../../../modules/scylladb/src/test/java/org/testcontainers/scylladb/ScyllaDBContainerTest.java) inside_block:custom_configuration
<!--/codeinclude-->

### Building CqlSession

<!--codeinclude-->
[Using CQL port](../../../modules/scylladb/src/test/java/org/testcontainers/scylladb/ScyllaDBContainerTest.java) inside_block:session
<!--/codeinclude-->

<!--codeinclude-->
[Using SSL](../../../modules/scylladb/src/test/java/org/testcontainers/scylladb/ScyllaDBContainerTest.java) inside_block:sslContext
<!--/codeinclude-->

<!--codeinclude-->
[Using SSL with cqlsh](../../../modules/scylladb/src/test/java/org/testcontainers/scylladb/ScyllaDBContainerTest.java) inside_block:sslCqlsh
<!--/codeinclude-->

<!--codeinclude-->
[Using Shard Awareness port](../../../modules/scylladb/src/test/java/org/testcontainers/scylladb/ScyllaDBContainerTest.java) inside_block:shardAwarenessSession
<!--/codeinclude-->

### Alternator

<!--codeinclude-->
[Enabling Alternator](../../../modules/scylladb/src/test/java/org/testcontainers/scylladb/ScyllaDBContainerTest.java) inside_block:alternator
<!--/codeinclude-->

<!--codeinclude-->
[DynamoDbClient with Alternator](../../../modules/scylladb/src/test/java/org/testcontainers/scylladb/ScyllaDBContainerTest.java) inside_block:dynamodDbClient
<!--/codeinclude-->

## Adding this module to your project dependencies

Add the following dependency to your `pom.xml`/`build.gradle` file:

=== "Gradle"
    ```groovy
    testImplementation "org.testcontainers:scylladb:{{latest_version}}"
    ```

=== "Maven"
    ```xml
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>scylladb</artifactId>
        <version>{{latest_version}}</version>
        <scope>test</scope>
    </dependency>
    ```
