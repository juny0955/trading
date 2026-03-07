package dev.junyoung.trading.order.adapter.in.rest.validation.annotation;

import dev.junyoung.trading.order.adapter.in.rest.validation.PlaceOrderValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PlaceOrderValidator.class)
public @interface ValidPlaceOrder {
    String message() default "Invalid order request";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
