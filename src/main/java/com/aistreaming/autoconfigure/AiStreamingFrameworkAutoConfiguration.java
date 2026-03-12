package com.aistreaming.autoconfigure;

import com.aistreaming.framework.config.StreamingProperties;
import com.aistreaming.framework.messaging.core.MessagingConsumerContainer;
import com.aistreaming.framework.messaging.core.MessagingPublisherBeanPostProcessor;
import com.aistreaming.framework.messaging.core.MessagingTemplate;
import com.aistreaming.framework.messaging.core.RedisMessagingBus;
import com.aistreaming.framework.support.JsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@EnableConfigurationProperties(StreamingProperties.class)
public class AiStreamingFrameworkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JsonCodec jsonCodec(ObjectMapper objectMapper) {
        return new JsonCodec(objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.streaming", name = "messaging-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public RedisMessagingBus redisMessagingBus(StringRedisTemplate redisTemplate,
                                               StreamingProperties properties,
                                               JsonCodec jsonCodec) {
        return new RedisMessagingBus(redisTemplate, properties, jsonCodec);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.streaming", name = "messaging-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public MessagingTemplate messagingTemplate(RedisMessagingBus redisMessagingBus, JsonCodec jsonCodec) {
        return new MessagingTemplate(redisMessagingBus, jsonCodec);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.streaming", name = "messaging-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public static MessagingPublisherBeanPostProcessor messagingPublisherBeanPostProcessor(MessagingTemplate messagingTemplate) {
        return new MessagingPublisherBeanPostProcessor(messagingTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.streaming", name = "messaging-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public MessagingConsumerContainer messagingConsumerContainer(RedisMessagingBus redisMessagingBus,
                                                                 JsonCodec jsonCodec,
                                                                 StreamingProperties properties) {
        return new MessagingConsumerContainer(redisMessagingBus, jsonCodec, properties);
    }
}

