package jakarta.persistence; import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) public @interface ManyToOne { FetchType fetch() default FetchType.EAGER; }
