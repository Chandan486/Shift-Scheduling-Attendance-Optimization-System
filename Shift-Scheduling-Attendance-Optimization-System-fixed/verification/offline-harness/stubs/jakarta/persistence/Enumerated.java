package jakarta.persistence; import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) public @interface Enumerated { EnumType value() default EnumType.ORDINAL; }
