package lk.thuhina.water.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Paged list response (design 7.1): {@code { items, page, size, total }}. */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
