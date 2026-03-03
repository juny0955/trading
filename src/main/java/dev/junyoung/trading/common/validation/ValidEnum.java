package dev.junyoung.trading.common.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EnumValidator.class)
public @interface ValidEnum {
	Class<? extends Enum<?>> enumClass();
	String message() default "Invalid enum value";
	Class<?>[] groups() default {};
	Class<? extends Payload>[] payload() default {};
}
