package dev.junyoung.trading.common.validation;

import java.util.Arrays;
import java.util.List;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EnumValidator implements ConstraintValidator<ValidEnum, String> {

	private List<String> validValues;

	@Override
	public void initialize(ValidEnum constraintAnnotation) {
		validValues = Arrays.stream(constraintAnnotation.enumClass().getEnumConstants())
			.map(Enum::name)
			.toList();
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null) return true;
		boolean valid = validValues.contains(value);
		if (!valid) {
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate("must be one of " + validValues).addConstraintViolation();
		}
		return valid;
	}

}
