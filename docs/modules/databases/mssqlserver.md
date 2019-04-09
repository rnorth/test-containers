# MS SQL Server Module

See [Database containers](./index.md) for documentation and usage that is common to all relational database container types.

## Usage example

Running MS SQL Server as a stand-in for in a test:

```java
public class SomeTest {

    @Rule
    public MSSQLServerContainer mssqlserver = new MSSQLServerContainer();
    
    @Test
    public void someTestMethod() {
        String url = mssqlserver.getJdbcUrl();

        ... create a connection and run test as normal
```

!!! warning "EULA Acceptance"
    Due to licencing restrictions you are required to accept an EULA for this container image. To indicate that you accept the MS SQL Server image EULA, Please place a file at the root of the classpath named `container-license-acceptance.txt`, e.g. at `src/test/resources/container-license-acceptance.txt`. This file should contain the line: `mcr.microsoft.com/mssql/server:2017-CU12` (or, if you are overriding the docker image name/tag, update accordingly).
    
    Please see the [`microsoft-mssql-server` image documentation](https://hub.docker.com/_/microsoft-mssql-server#environment-variables) for a link to the EULA document.

## Adding this module to your project dependencies

Add the following dependency to your `pom.xml`/`build.gradle` file:

```groovy tab='Gradle'
testCompile "org.testcontainers:mssqlserver:{{latest_version}}"
```

```xml tab='Maven'
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mssqlserver</artifactId>
    <version>{{latest_version}}</version>
    <scope>test</scope>
</dependency>
```


!!! hint
    Adding this Testcontainers library JAR will not automatically add a database driver JAR to your project. You should ensure that your project also has a suitable database driver as a dependency.

## License

See [LICENSE](https://raw.githubusercontent.com/testcontainers/testcontainers-java/master/modules/mssqlserver/LICENSE).

## Copyright

Copyright (c) 2017 - 2019 G DATA Software AG and other authors.

See [AUTHORS](https://raw.githubusercontent.com/testcontainers/testcontainers-java/master/modules/mssqlserver/AUTHORS) for contributors.
