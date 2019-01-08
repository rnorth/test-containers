# Neo4j container

This module helps running [Neo4j](https://neo4j.com/download/) using Testcontainers.

Note that it's based on the [official Docker image](https://hub.docker.com/_/neo4j/) provided by Neo4j, Inc.

## Dependencies

Add the Neo4j Testcontainer module:

```groovy
testCompile "org.testcontainers:neo4j"
```

and the Neo4j Java driver if you plan to access the Testcontainer via Bolt:

```groovy
compile "org.neo4j.driver:neo4j-java-driver:1.7.1"
```

## Usage example

Declare your Testcontainer as a `@ClassRule` or `@Rule` in a JUnit 4 test or as static or member attribute of a JUnit 5 test annotated with `@Container` as you would with other Testcontainers.
You can either use call `getHttpUrl()` or `getBoltUrl()` on the Neo4j container.
`getHttpUrl()` gives you the HTTP-address of the transactional HTTP endpoint while `getBoltUrl()` is meant to be used with one of the [official Bolt drivers](https://neo4j.com/docs/developer-manual/preview/drivers/).
On the JVM you would most likely use the [Java driver](https://github.com/neo4j/neo4j-java-driver).

The following example uses the JUnit 5 extension `@Testcontainers` and demonstrates both the usage of the Java Driver and the REST endpoint:

```java
@Testcontainers
public class ExampleTest {

    @Container
    private static Neo4jContainer neo4jContainer = new Neo4jContainer()
        .withAdminPassword(null); // Disable password

    @Test
    void testSomethingUsingBolt() {

        // Retrieve the Bolt URL from the container
        String boltUrl = neo4jContainer.getBoltUrl();
        try (
            Driver driver = GraphDatabase.driver(boltUrl, AuthTokens.none());
            Session session = driver.session()
        ) {
            long one = session.run("RETURN 1", Collections.emptyMap()).next().get(0).asLong();
            assertThat(one, is(1L));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    void testSomethingUsingHttp() throws IOException {

        // Retrieve the HTTP URL from the container
        String httpUrl = neo4jContainer.getHttpUrl();

        URL url = new URL(httpUrl + "/db/data/transaction/commit");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);

        try (Writer out = new OutputStreamWriter(con.getOutputStream())) {
            out.write("{\"statements\":[{\"statement\":\"RETURN 1\"}]}");
            out.flush();
        }

        assertThat(con.getResponseCode(), is(HttpURLConnection.HTTP_OK));
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String expectedResponse = 
                "{\"results\":[{\"columns\":[\"1\"],\"data\":[{\"row\":[1],\"meta\":[null]}]}],\"errors\":[]}";
            String response = buffer.lines().collect(Collectors.joining("\n"));
            assertThat(response, is(expectedResponse));
        }
    }
}
```

You are not limited to Unit tests and can of course use an instance of the Neo4j Testcontainer in vanilla Java code as well.


## Choose your Neo4j license

If you need the Neo4j enterprise license, you can declare your Neo4j container like this:

```java
@Testcontainers
public class ExampleTest { 
    @ClassRule
    public static Neo4jContainer neo4jContainer = new Neo4jContainer()
        .withEnterpriseEdition();        
}
```

This creates a Testcontainer based on the Docker image build with the Enterprise version of Neo4j. 
The call to `withEnterpriseEdition` adds the required environment variable that you accepted the terms and condition of the enterprise version.
You accept those by adding a file named `container-license-acceptance.txt` to the root of your classpath containing the text `neo4j:3.5.0-enterprise` in one line.
You'll find more information about licensing Neo4j here: [About Neo4j Licenses](https://neo4j.com/licensing/).
