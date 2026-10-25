package jakarta.persistence; import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) public @interface UniqueConstraint { String name() default ""; String[] columnNames() default {}; }
