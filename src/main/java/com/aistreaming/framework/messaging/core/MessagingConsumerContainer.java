package com.aistreaming.framework.messaging.core;

import com.aistreaming.framework.config.StreamingProperties;
import com.aistreaming.framework.messaging.annotation.Consumer;
import com.aistreaming.framework.messaging.annotation.MessagingService;
import com.aistreaming.framework.service.StreamEnvelope;
import com.aistreaming.framework.support.JsonCodec;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

@Slf4j
public class MessagingConsumerContainer implements SmartInitializingSingleton, DisposableBean, BeanFactoryAware {

    private final RedisMessagingBus messagingBus;
    private final JsonCodec jsonCodec;
    private final StreamingProperties properties;
    private final AtomicInteger workerCounter = new AtomicInteger();
    private final List<ExecutorService> executors = new ArrayList<ExecutorService>();
    private final List<MessagingConsumerEndpoint> endpoints = new ArrayList<MessagingConsumerEndpoint>();
    private ConfigurableListableBeanFactory beanFactory;
    private volatile boolean running = true;

    public MessagingConsumerContainer(RedisMessagingBus messagingBus, JsonCodec jsonCodec, StreamingProperties properties) {
        this.messagingBus = messagingBus;
        this.jsonCodec = jsonCodec;
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!properties.isMessagingEnabled()) {
            return;
        }
        Map<String, Object> beans = beanFactory.getBeansWithAnnotation(MessagingService.class);
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            registerEndpoints(entry.getKey(), entry.getValue());
        }
        for (MessagingConsumerEndpoint endpoint : endpoints) {
            startEndpoint(endpoint);
        }
    }

    @Override
    public void destroy() {
        running = false;
        for (ExecutorService executor : executors) {
            executor.shutdownNow();
        }
    }

    @Override
    public void setBeanFactory(org.springframework.beans.factory.BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }

    private void registerEndpoints(String beanName, Object bean) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        MessagingService messagingService = AnnotatedElementUtils.findMergedAnnotation(targetClass, MessagingService.class);
        if (messagingService == null) {
            return;
        }
        String serviceName = resolveServiceName(targetClass, messagingService);
        ReflectionUtils.doWithMethods(targetClass, method -> {
            Consumer consumer = AnnotatedElementUtils.findMergedAnnotation(method, Consumer.class);
            if (consumer == null) {
                return;
            }
            Method invocableMethod = AopUtils.selectInvocableMethod(method, bean.getClass());
            String binding = resolveBinding(serviceName, method.getName(), consumer.binding());
            String group = resolveGroup(serviceName, method.getName(), consumer.group());
            String consumerName = properties.getNodeId() + "-" + beanName + "-" + method.getName();
            endpoints.add(MessagingConsumerEndpoint.create(bean, invocableMethod, serviceName, binding, group, consumerName));
            log.info("Registered messaging consumer {}#{} for binding {}", targetClass.getSimpleName(), method.getName(), binding);
        }, method -> !method.isBridge() && !method.isSynthetic());
    }

    private void startEndpoint(final MessagingConsumerEndpoint endpoint) {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("ai-messaging-consumer-" + workerCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        executors.add(executor);
        executor.submit(() -> consume(endpoint));
    }

    private void consume(MessagingConsumerEndpoint endpoint) {
        while (running) {
            try {
                List<StreamEnvelope<MessagingRecord>> envelopes = messagingBus.read(
                    endpoint.getBinding(),
                    endpoint.getGroup(),
                    endpoint.getConsumerName()
                );
                for (StreamEnvelope<MessagingRecord> envelope : envelopes) {
                    endpoint.invoke(envelope.getPayload(), jsonCodec);
                    messagingBus.acknowledge(envelope);
                }
            } catch (Exception ex) {
                if (running) {
                    log.warn("Messaging consumer loop failed for binding {}", endpoint.getBinding(), ex);
                }
            }
        }
    }

    private String resolveServiceName(Class<?> targetClass, MessagingService messagingService) {
        return StringUtils.hasText(messagingService.service()) ? messagingService.service() : targetClass.getSimpleName();
    }

    private String resolveBinding(String serviceName, String operation, String explicitBinding) {
        return StringUtils.hasText(explicitBinding) ? explicitBinding : serviceName + "." + operation;
    }

    private String resolveGroup(String serviceName, String operation, String explicitGroup) {
        if (StringUtils.hasText(explicitGroup)) {
            return explicitGroup;
        }
        return properties.getMessagingConsumerGroupPrefix() + serviceName + "." + operation;
    }
}
