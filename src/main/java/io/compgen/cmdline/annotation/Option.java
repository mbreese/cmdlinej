package io.compgen.cmdline.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value = ElementType.METHOD)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface Option {
	String desc() default "";

	String charName() default "";
	String name() default "";

	String defaultValue() default "";
	String defaultText() default "";
	String helpValue() default "";
	boolean required() default false;

	boolean allowMultiple() default false;

	boolean hide() default false;
	boolean showHelp() default false;
}
