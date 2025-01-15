# Liberty Containers

Testcontainers can be used to automatically instantiate and manage [Open Liberty](https://openliberty.io/) and [WebSphere Liberty](https://www.ibm.com/products/websphere-liberty/) containers.
More precisely Testcontainers uses the official Docker images for [Open Liberty](https://hub.docker.com/_/open-liberty) or [WebSphere Liberty](https://hub.docker.com/_/websphere-liberty)

## Benefits

* Easier integration testing for application developers.
* Easier functional testing for platform development.

## Example

Create a `LibertyContainer` to use it in your tests:
<!--codeinclude-->
[Creating a LibertyContainer](../../modules/liberty/src/test/java/org/testcontainers/liberty/LibertyContainerTest.java) inside_block:constructorWithVersion
<!--/codeinclude-->

Now you can perform integration testing, in this example we are using [RestAssured](https://rest-assured.io/) to query a RESTful web service running in Liberty.

<!--codeinclude-->
[RESTful Test](../../modules/liberty/src/test/java/org/testcontainers/liberty/LibertyContainerTest.java) inside_block:testRestEndpoint
<!--/codeinclude-->

## Multi-container usage

If your Liberty server needs to connect to a data provider, message provider, 
or other service that can also be run as a container you can connect them using a network:

* Run your other container on the same network as Liberty container, e.g.:
<!--codeinclude-->
[Network](../../modules/liberty/src/test/java/org/testcontainers/liberty/LibertyContainerTest.java) inside_block:constructorMockDatabase
<!--/codeinclude-->
* Use network aliases and unmapped ports to configure an environment variable that can be access from your Application server.
<!--codeinclude-->
[Configure Liberty](../../modules/liberty/src/test/java/org/testcontainers/liberty/LibertyContainerTest.java) inside_block:configureLiberty
<!--/codeinclude-->

You will need to explicitly create a network and set it on the Liberty container as well as on your other containers that Liberty communicates with.

## Adding this module to your project dependencies

Add the following dependency to your `pom.xml`/`build.gradle` file:

=== "Gradle"
```groovy
testImplementation "org.testcontainers:liberty:{{latest_version}}"
```
=== "Maven"
```xml
<dependency>
<groupId>org.testcontainers</groupId>
<artifactId>liberty</artifactId>
<version>{{latest_version}}</version>
<scope>test</scope>
</dependency>
```
