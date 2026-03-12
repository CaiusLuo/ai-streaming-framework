package com.aistreaming.framework.messaging.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Consumer {

    @AliasFor("binding")
    String value() default "";

    @AliasFor("value")
    String binding() default "";

    String group() default "";
}
