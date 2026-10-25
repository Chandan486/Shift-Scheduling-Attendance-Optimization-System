package org.springframework.data.domain; import java.util.function.Function;
public interface Page<T> { <R> Page<R> map(Function<? super T, ? extends R> f); }
