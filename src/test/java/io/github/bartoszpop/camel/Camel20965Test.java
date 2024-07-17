package io.github.bartoszpop.camel;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static java.nio.file.Files.readString;
import static org.apache.camel.WaitForTaskToComplete.Never;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@SpringBootConfiguration
@EnableAutoConfiguration
@WireMockTest
public class Camel20965Test {

  @Autowired
  private CamelContext camelContext;

  @Autowired
  private ProducerTemplate producerTemplate;

  @EndpointInject("mock:output")
  private MockEndpoint outputMock;

  @Test
  void convertStreamToString_resetStreamInParallel_stringHasDuplicates(WireMockRuntimeInfo mockServer)
      throws Exception {
    // Arrange
    var veryBigFile = readString(Paths.get("src/test/resources/VeryBigFile.txt"));
    stubFor(get("/").willReturn(ok(veryBigFile)));//.withHeader("Content-Type", "text/plain; charset=utf-8")));
    camelContext.addRoutes(new EndpointRouteBuilder() {
      @Override
      public void configure() {
        from(direct("first"))
            .to(mockServer.getHttpBaseUrl() + "/")
            .to(seda("second").waitForTaskToComplete(Never))
            .process(ex -> Thread.sleep(10));

        from(seda("second"))
            .convertBodyTo(String.class)
            .to(outputMock);
      }
    });
    outputMock.expectedMessageCount(1);

    // Act
    producerTemplate.sendBody("direct:first", null);

    // Assert
    outputMock.assertIsSatisfied();
    assertEquals(veryBigFile, outputMock.getReceivedExchanges().getFirst().getMessage().getBody());
  }
}
