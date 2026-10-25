package jakarta.persistence; import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) public @interface Column { String name() default ""; boolean nullable() default true; boolean updatable() default true; int length() default 255; String columnDefinition() default ""; }
