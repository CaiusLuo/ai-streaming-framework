package com.aistreaming.framework.messaging.core;

import com.aistreaming.framework.support.JsonCodec;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class MessagingConsumerEndpoint {

    private final Object bean;
    private final Method method;
    private final String serviceName;
    private final String binding;
    private final String group;
    private final String consumerName;
    private final Class<?> payloadType;
    private final boolean messagingRecordParameter;

    private MessagingConsumerEndpoint(Object bean,
                                      Method method,
                                      String serviceName,
                                      String binding,
                                      String group,
                                      String consumerName,
                                      Class<?> payloadType,
                                      boolean messagingRecordParameter) {
        this.bean = bean;
        this.method = method;
        this.serviceName = serviceName;
        this.binding = binding;
        this.group = group;
        this.consumerName = consumerName;
        this.payloadType = payloadType;
        this.messagingRecordParameter = messagingRecordParameter;
    }

    public static MessagingConsumerEndpoint create(Object bean,
                                                   Method method,
                                                   String serviceName,
                                                   String binding,
                                                   String group,
                                                   String consumerName) {
        if (method.getParameterCount() > 1) {
            throw new IllegalStateException("Messaging consumer method must declare zero or one argument: " + method);
        }
        Class<?> payloadType = null;
        boolean messagingRecordParameter = false;
        if (method.getParameterCount() == 1) {
            Class<?> parameterType = method.getParameterTypes()[0];
            if (MessagingRecord.class.isAssignableFrom(parameterType)) {
                messagingRecordParameter = true;
            } else {
                payloadType = parameterType;
            }
        }
        return new MessagingConsumerEndpoint(
            bean,
            method,
            serviceName,
            binding,
            group,
            consumerName,
            payloadType,
            messagingRecordParameter
        );
    }

    public void invoke(MessagingRecord record, JsonCodec jsonCodec) {
        Object[] arguments = resolveArguments(record, jsonCodec);
        try {
            method.setAccessible(true);
            method.invoke(bean, arguments);
        } catch (InvocationTargetException ex) {
            Throwable targetException = ex.getTargetException();
            if (targetException instanceof RuntimeException) {
                throw (RuntimeException) targetException;
            }
            throw new IllegalStateException("Failed to invoke messaging consumer method " + method, targetException);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to invoke messaging consumer method " + method, ex);
        }
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getBinding() {
        return binding;
    }

    public String getGroup() {
        return group;
    }

    public String getConsumerName() {
        return consumerName;
    }

    private Object[] resolveArguments(MessagingRecord record, JsonCodec jsonCodec) {
        if (method.getParameterCount() == 0) {
            return new Object[0];
        }
        if (messagingRecordParameter) {
            return new Object[]{record};
        }
        Object payload = record.getPayload() == null ? null : jsonCodec.fromJson(record.getPayload(), payloadType);
        return new Object[]{payload};
    }
}
