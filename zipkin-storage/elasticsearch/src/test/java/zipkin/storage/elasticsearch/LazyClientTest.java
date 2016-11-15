/**
 * Copyright 2015-2016 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin.storage.elasticsearch;

import java.io.IOException;
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequest;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.elasticsearch.common.transport.TransportAddress;
import org.junit.AssumptionViolatedException;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class LazyClientTest {

  @Test
  public void testToString() {
    LazyClient lazyClient = new LazyClient(ElasticsearchStorage.builder()
        .cluster("cluster")
        .hosts(asList("host1", "host2")));

    assertThat(lazyClient)
        .hasToString("{\"clusterName\": \"cluster\", \"hosts\": [\"host1\", \"host2\"]}");
  }

  @Test
  public void defaultShardAndReplicaCount() {
    LazyClient lazyClient = new LazyClient(ElasticsearchStorage.builder());

    assertThat(lazyClient.versionSpecificTemplate("2.4.0"))
        .contains("    \"index.number_of_shards\": 5,\n"
            + "    \"index.number_of_replicas\": 1,");
  }

  @Test
  public void overrideShardAndReplicaCount() {
    LazyClient lazyClient = new LazyClient(ElasticsearchStorage.builder()
        .indexShards(30)
        .indexReplicas(0));

    assertThat(lazyClient.versionSpecificTemplate("2.4.0"))
        .contains("    \"index.number_of_shards\": 30,\n"
            + "    \"index.number_of_replicas\": 0,");
  }

  @Test
  public void defaultsToUnanalyzedTraceId_2x() {
    LazyClient lazyClient = new LazyClient(ElasticsearchStorage.builder());

    PutIndexTemplateRequest request =
        new PutIndexTemplateRequest("zipkin").source(lazyClient.versionSpecificTemplate("2.4.0"));

    assertThat(request.mappings().get("span"))
        .contains("\"traceId\":{\"type\":\"string\",\"index\":\"not_analyzed\"}");
  }

  @Test
  public void defaultsToKeywordTraceId_5x() {
    LazyClient lazyClient = new LazyClient(ElasticsearchStorage.builder());

    PutIndexTemplateRequest request =
        new PutIndexTemplateRequest("zipkin").source(lazyClient.versionSpecificTemplate("5.0.0"));

    assertThat(request.mappings().get("span"))
        .contains("\"traceId\":{\"type\":\"keyword\"}");
  }

  @Test
  public void tokenizedTraceId_2x() {
    LazyClient lazyClient = new LazyClient(ElasticsearchStorage.builder().strictTraceId(false));

    PutIndexTemplateRequest request =
        new PutIndexTemplateRequest("zipkin").source(lazyClient.versionSpecificTemplate("2.4.0"));

    assertThat(request.mappings().get("span"))
        .contains("\"traceId\":{\"type\":\"string\",\"analyzer\":\"traceId_analyzer\"}");
  }

  /** Also notice, fielddata must be true in this case (which is expensive) */
  @Test
  public void tokenizedTraceId_5x() {
    LazyClient lazyClient = new LazyClient(ElasticsearchStorage.builder().strictTraceId(false));

    PutIndexTemplateRequest request =
        new PutIndexTemplateRequest("zipkin").source(lazyClient.versionSpecificTemplate("5.0.0"));

    assertThat(request.mappings().get("span"))
        .contains(
            "\"traceId\":{\"type\":\"string\",\"fielddata\":\"true\",\"analyzer\":\"traceId_analyzer\"}");
  }

  @Test
  public void portDefaultsTo9300() throws IOException {
    try (LazyClient lazyClient = new LazyClient(ElasticsearchStorage.builder()
        .hosts(asList("localhost")))) {

      assertThat(((NativeClient) lazyClient.get()).client.transportAddresses())
          .extracting(TransportAddress::getPort)
          .containsOnly(9300);
    } catch (NoNodeAvailableException e) {
      throw new AssumptionViolatedException(e.getMessage());
    }
  }
}
