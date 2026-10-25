package jakarta.validation.constraints; import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) public @interface NotNull { String message() default ""; }
