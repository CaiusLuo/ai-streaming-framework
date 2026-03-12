package com.aistreaming.framework.messaging.core;

import com.aistreaming.framework.messaging.annotation.MessagingService;
import com.aistreaming.framework.messaging.annotation.Publisher;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

public class MessagingPublisherBeanPostProcessor implements BeanPostProcessor {

    private final MessagingTemplate messagingTemplate;

    public MessagingPublisherBeanPostProcessor(MessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        MessagingService messagingService = AnnotatedElementUtils.findMergedAnnotation(targetClass, MessagingService.class);
        if (messagingService == null) {
            return bean;
        }
        Map<Method, Publisher> publisherMethods = resolvePublisherMethods(targetClass);
        if (publisherMethods.isEmpty()) {
            return bean;
        }

        String serviceName = resolveServiceName(targetClass, messagingService);
        ProxyFactory proxyFactory = new ProxyFactory(bean);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new PublisherMethodInterceptor(targetClass, serviceName, publisherMethods, messagingTemplate));
        return proxyFactory.getProxy();
    }

    private Map<Method, Publisher> resolvePublisherMethods(Class<?> targetClass) {
        Map<Method, Publisher> publisherMethods = new LinkedHashMap<Method, Publisher>();
        ReflectionUtils.doWithMethods(targetClass, method -> {
            Publisher publisher = AnnotatedElementUtils.findMergedAnnotation(method, Publisher.class);
            if (publisher == null) {
                return;
            }
            validatePublisherMethod(method, publisher);
            publisherMethods.put(method, publisher);
        }, method -> !method.isBridge() && !method.isSynthetic());
        return publisherMethods;
    }

    private void validatePublisherMethod(Method method, Publisher publisher) {
        if (method.getParameterCount() > 1) {
            throw new IllegalStateException("Messaging publisher method must declare zero or one argument: " + method);
        }
        if (!publisher.invokeTarget()
            && !Void.TYPE.equals(method.getReturnType())
            && !MessagePublishResult.class.isAssignableFrom(method.getReturnType())) {
            throw new IllegalStateException(
                "Publisher method with invokeTarget=false must return void or MessagePublishResult: " + method
            );
        }
    }

    private String resolveServiceName(Class<?> targetClass, MessagingService messagingService) {
        return StringUtils.hasText(messagingService.service()) ? messagingService.service() : targetClass.getSimpleName();
    }

    private static final class PublisherMethodInterceptor implements MethodInterceptor {

        private final Class<?> targetClass;
        private final String serviceName;
        private final Map<Method, Publisher> publisherMethods;
        private final MessagingTemplate messagingTemplate;

        private PublisherMethodInterceptor(Class<?> targetClass,
                                           String serviceName,
                                           Map<Method, Publisher> publisherMethods,
                                           MessagingTemplate messagingTemplate) {
            this.targetClass = targetClass;
            this.serviceName = serviceName;
            this.publisherMethods = publisherMethods;
            this.messagingTemplate = messagingTemplate;
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            Method specificMethod = ClassUtils.getMostSpecificMethod(invocation.getMethod(), targetClass);
            Publisher publisher = publisherMethods.get(specificMethod);
            if (publisher == null) {
                return invocation.proceed();
            }

            Object result = null;
            if (publisher.invokeTarget()) {
                result = invocation.proceed();
            }

            String binding = StringUtils.hasText(publisher.binding())
                ? publisher.binding()
                : serviceName + "." + specificMethod.getName();
            MessagePublishResult publishResult = messagingTemplate.publish(
                serviceName,
                specificMethod.getName(),
                binding,
                extractPayload(specificMethod, invocation.getArguments())
            );

            if (!publisher.invokeTarget()) {
                if (Void.TYPE.equals(specificMethod.getReturnType())) {
                    return null;
                }
                return publishResult;
            }

            if (result == null && MessagePublishResult.class.isAssignableFrom(specificMethod.getReturnType())) {
                return publishResult;
            }
            return result;
        }

        private Object extractPayload(Method method, Object[] arguments) {
            if (arguments == null || arguments.length == 0) {
                return null;
            }
            if (arguments.length > 1) {
                throw new IllegalStateException("Messaging publisher method must declare zero or one argument: " + method);
            }
            return arguments[0];
        }
    }
}
