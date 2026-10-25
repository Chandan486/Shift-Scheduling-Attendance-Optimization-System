package org.springframework.data.repository.query; import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) public @interface Param { String value(); }
