package com.fasterxml.jackson.annotation; import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) public @interface JsonIgnoreProperties { boolean ignoreUnknown() default false; }
