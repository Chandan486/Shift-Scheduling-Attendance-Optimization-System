package jakarta.persistence; import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) public @interface GeneratedValue { GenerationType strategy() default GenerationType.AUTO; }
