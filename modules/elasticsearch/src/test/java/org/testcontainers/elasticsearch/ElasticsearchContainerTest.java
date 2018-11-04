package org.testcontainers.elasticsearch;


import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.rnorth.visibleassertions.VisibleAssertions.assertThrows;
import static org.testcontainers.elasticsearch.ElasticsearchContainer.ELASTICSEARCH_DEFAULT_VERSION;

public class ElasticsearchContainerTest {

    /**
     * Elasticsearch default username, when secured with a license > basic
     */
    private static final String ELASTICSEARCH_USERNAME = "elastic";

    /**
     * Elasticsearch 5.x default password. In 6.x images, there's no security by default as shipped with a basic license.
     */
    private static final String ELASTICSEARCH_PASSWORD = "changeme";

    private RestClient client = null;

    @After
    public void stopRestClient() throws IOException {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    @Test
    public void elasticsearchDefaultTest() throws IOException {
        try (ElasticsearchContainer container = new ElasticsearchContainer()){
            container.start();
            Response response = getClient(container).performRequest(new Request("GET", "/"));
            assertThat(response.getStatusLine().getStatusCode(), is(200));
            assertThat(EntityUtils.toString(response.getEntity()), containsString(ELASTICSEARCH_DEFAULT_VERSION));

            // The default image is running with the features under Elastic License
            response = getClient(container).performRequest(new Request("GET", "/_xpack/"));
            assertThat(response.getStatusLine().getStatusCode(), is(200));
            // For now we test that we have the monitoring feature available
            assertThat(EntityUtils.toString(response.getEntity()), containsString("monitoring"));
        }
    }

    @Test
    public void elasticsearchVersion() throws IOException {
        try (ElasticsearchContainer container = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:5.6.12")) {
            container.start();
            Response response = getClient(container).performRequest(new Request("GET", "/"));
            assertThat(response.getStatusLine().getStatusCode(), is(200));
            String responseAsString = EntityUtils.toString(response.getEntity());
            assertThat(responseAsString, containsString("5.6.12"));
        }
    }

    @Test
    public void elasticsearchOssImage() throws IOException {
        try (ElasticsearchContainer container =
                 new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch-oss:" + ELASTICSEARCH_DEFAULT_VERSION)) {
            container.start();
            Response response = getClient(container).performRequest(new Request("GET", "/"));
            assertThat(response.getStatusLine().getStatusCode(), is(200));
            // The OSS image does not have any feature under Elastic License
            assertThrows("We should not have /_xpack endpoint with an OSS License",
                ResponseException.class,
                () -> getClient(container).performRequest(new Request("GET", "/_xpack/")));
        }
    }

    private RestClient getClient(ElasticsearchContainer container) {
        if (client == null) {
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(ELASTICSEARCH_USERNAME, ELASTICSEARCH_PASSWORD));

            client = RestClient.builder(container.getHost())
                    .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
                    .build();
        }

        return client;
    }
}
