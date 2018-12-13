# Testcontainers

> Testcontainers is a Java library that supports JUnit tests, providing lightweight, throwaway instances of common databases, Selenium web browsers, or anything else that can run in a Docker container.

[![Build Status](https://travis-ci.org/testcontainers/testcontainers-java.svg?branch=master)](https://travis-ci.org/testcontainers/testcontainers-java)
[View on Github](https://github.com/testcontainers/testcontainers-java)

![Testcontainers logo](https://rnorth.org/public/testcontainers/logo.png)

## Use Cases

Testcontainers makes it easy to launch useful Docker containers for the duration of JUnit tests.

 * **Data access layer integration tests**: use a containerized instance of a MySQL, PostgreSQL or Oracle database to test your data access layer code for complete compatibility, but without requiring complex setup on developers' machines and safe in the knowledge that your tests will always start with a known DB state. Any other database type that can be containerized can also be used.
 * **Application integration tests**: for running your application in a short-lived test mode with dependencies, such as databases, message queues or web servers.
 * **UI/Acceptance tests**: use containerized web browsers, compatible with Selenium, for conducting automated UI tests. Each test can get a fresh instance of the browser, with no browser state, plugin variations or automated browser upgrades to worry about. And you get a video recording of each test session, or just each session where tests failed.
 * **Much more!** Check out the various [contributed modules](https://github.com/testcontainers) or create your own custom container classes using [`GenericContainer`](usage/generic_containers.md) as a base.

## Who is using Testcontainers?

 * [ZeroTurnaround](https://zeroturnaround.com) - Testing of the Java Agents, micro-services, Selenium browser automation
 * [Zipkin](http://zipkin.io) - MySQL and Cassandra testing
 * [Apache Gora](https://gora.apache.org) - CouchDB testing
 * [Apache James](https://james.apache.org) - LDAP and Cassandra integration testing
 * [StreamSets](https://github.com/streamsets/datacollector) - LDAP, MySQL Vault, MongoDB, Redis integration testing
 * [Playtika](https://github.com/Playtika/testcontainers-spring-boot) - Kafka, Couchbase, MariaDB, Redis, Neo4j, Aerospike, MemSQL
 * [JetBrains](https://www.jetbrains.com/) - Testing of the TeamCity plugin for HashiCorp Vault
 * [Plumbr](https://plumbr.io) - Integration testing of data processing pipeline micro-services
 * [Streamlio](https://streaml.io/) - Integration and Chaos Testing of our fast data platform based on Apache Puslar, Apache Bookeeper and Apache Heron.
 * [Spring Session](https://projects.spring.io/spring-session/) - Redis, PostgreSQL, MySQL and MariaDB integration testing
 * [Apache Camel](https://camel.apache.org) - Testing Camel against native services such as Consul, Etcd and so on
 * [Instana](https://www.instana.com) - Testing agents and stream processing backends
 * [eBay Marketing](https://www.ebay.com) - Testing for MySQL, Cassandra, Redis, Couchbase, Kafka, etc.
 * [Skyscanner](https://www.skyscanner.net/) - Integration testing against HTTP service mocks and various data stores
 * [Neo4j-OGM](https://neo4j.com/developer/neo4j-ogm/) - Testing new, reactive client implementations

## Getting help

You can:

* Join our Slack: http://slack.testcontainers.org
* [Search our issues tracker](https://github.com/testcontainers/testcontainers-java/issues), or raise a new issue if you find any bugs or have suggested improvements

## License

See [LICENSE](https://raw.githubusercontent.com/testcontainers/testcontainers-java/master/LICENSE).

## Attributions

This project includes a modified class (ScriptUtils) taken from the Spring JDBC project, adapted under the terms of the Apache license. Copyright for that class remains with the original authors.

This project was initially inspired by a [gist](https://gist.github.com/mosheeshel/c427b43c36b256731a0b) by [Moshe Eshel](https://github.com/mosheeshel).

## Contributing

See [CONTRIBUTING](https://github.com/testcontainers/testcontainers-java/blob/master/CONTRIBUTING.md).

## Copyright

Copyright (c) 2015, 2016 Richard North and other authors.

See [AUTHORS](https://raw.githubusercontent.com/testcontainers/testcontainers-java/master/AUTHORS) for contributors.
