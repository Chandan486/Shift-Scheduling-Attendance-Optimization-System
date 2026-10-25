package org.springframework.data.jpa.repository; import java.lang.annotation.*; import jakarta.persistence.LockModeType;
@Retention(RetentionPolicy.RUNTIME) public @interface Lock { LockModeType value(); }
