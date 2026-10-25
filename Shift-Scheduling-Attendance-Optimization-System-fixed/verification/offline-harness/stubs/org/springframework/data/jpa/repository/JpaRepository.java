package org.springframework.data.jpa.repository; import java.util.*;
public interface JpaRepository<T, ID> {
    <S extends T> S save(S e);
    <S extends T> S saveAndFlush(S e);
    void flush();
    Optional<T> findById(ID id);
    boolean existsById(ID id);
    List<T> findAll();
    void deleteAll(Iterable<? extends T> entities);
    void deleteAll();
}
