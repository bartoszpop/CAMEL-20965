package io.github.bartoszpop.camel;

import static java.lang.Thread.sleep;
import static org.apache.camel.component.kamelet.Kamelet.extractTemplateId;
import static org.apache.camel.component.kamelet.Kamelet.templateToRoute;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.component.kamelet.KameletComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultModel;
import org.apache.camel.model.Model;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ModelLifecycleStrategySupport;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplateDefinition.Converter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootTest
class Camel20614Test {

  @Autowired
  private ProducerTemplate producerTemplate;

  @Autowired
  private ModelCamelContext modelCamelContext;

  @EndpointInject("mock:some")
  private MockEndpoint someMock;

  @Test
  void createSameKameletTwiceInParallel_KameletConsumerNotAvailableExceptionThrown() throws InterruptedException {
    // Arrange
    var latch = new CountDownLatch(2);
    modelCamelContext.addRouteTemplateDefinitionConverter("*", (in, parameters) -> {
      try {
        return templateToRoute(in, parameters);
      } finally {
        latch.countDown();
        latch.await();
      }
    });
    someMock.expectedMessageCount(2);

    // Act
    producerTemplate.sendBody("seda:route", null);
    producerTemplate.requestBody("seda:route", ((Object)null));

    // Assert
    someMock.assertIsSatisfied();
  }

  @Configuration
  @EnableAutoConfiguration
  static class Config {

    @Bean
    public RouteBuilder sampleRoute() {
      return new EndpointRouteBuilder() {
        @Override
        public void configure() {
          from(seda("route").concurrentConsumers(2))
              .toD("kamelet:-");
        }
      };
    }

    @Bean
    public RouteBuilder sampleRouteTemplate() {
      return new EndpointRouteBuilder() {
        @Override
        public void configure() {
          routeTemplate("-"). // This is a workaround for "*" to be iterated before templateId at org.apache.camel.impl.DefaultModel#addRouteFromTemplate (line 460)
              from("kamelet:source")
              .to("mock:some");
        }
      };
    }
  }
}
