package jakarta.validation.constraints; import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) public @interface Size { int min() default 0; int max() default Integer.MAX_VALUE; String message() default ""; }
