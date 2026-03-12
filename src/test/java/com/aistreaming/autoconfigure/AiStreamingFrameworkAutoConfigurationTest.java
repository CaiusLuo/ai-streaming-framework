package com.aistreaming.autoconfigure;

import com.aistreaming.framework.messaging.core.MessagingConsumerContainer;
import com.aistreaming.framework.messaging.core.MessagingPublisherBeanPostProcessor;
import com.aistreaming.framework.messaging.core.MessagingTemplate;
import com.aistreaming.framework.messaging.core.RedisMessagingBus;
import com.aistreaming.framework.support.JsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiStreamingFrameworkAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AiStreamingFrameworkAutoConfiguration.class))
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));

    @Test
    void shouldRegisterAutoConfigurationInImportsFile() throws Exception {
        ClassPathResource resource = new ClassPathResource(
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
        );

        assertThat(resource.exists()).isTrue();

        List<String> imports;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            imports = reader.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());
        }

        assertThat(imports).contains(AiStreamingFrameworkAutoConfiguration.class.getName());
    }

    @Test
    void shouldConfigureMessagingInfrastructureWhenEnabled() {
        contextRunner.run(context -> {
            assertMessagingBeansPresent(context);
        });
    }

    @Test
    void shouldBackOffMessagingInfrastructureWhenDisabled() {
        contextRunner.withPropertyValues("ai.streaming.messaging-enabled=false").run(context -> {
            assertThat(context).hasSingleBean(JsonCodec.class);
            assertThat(context).doesNotHaveBean(RedisMessagingBus.class);
            assertThat(context).doesNotHaveBean(MessagingTemplate.class);
            assertThat(context).doesNotHaveBean(MessagingPublisherBeanPostProcessor.class);
            assertThat(context).doesNotHaveBean(MessagingConsumerContainer.class);
        });
    }

    private void assertMessagingBeansPresent(AssertableApplicationContext context) {
        assertThat(context).hasSingleBean(JsonCodec.class);
        assertThat(context).hasSingleBean(RedisMessagingBus.class);
        assertThat(context).hasSingleBean(MessagingTemplate.class);
        assertThat(context).hasSingleBean(MessagingPublisherBeanPostProcessor.class);
        assertThat(context).hasSingleBean(MessagingConsumerContainer.class);
    }
}