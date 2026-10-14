package lk.thuhina.water.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

/**
 * The OpenAPI description (and so the client's API types) names each schema by the record's simple name: two DTOs with the
 * same name silently become one (billing's payment records once hid the supplier-payment ones). Every DTO name must be unique.
 */
class DtoNamesTest {

    @Test
    void dtoRecordNamesAreUnique() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*\\.dto\\..*")));
        List<String> names = scanner.findCandidateComponents("lk.thuhina.water").stream()
                .map(BeanDefinition::getBeanClassName)
                .toList();
        assertThat(names).hasSizeGreaterThan(100);
        Map<String, List<String>> bySimpleName = names.stream()
                .collect(Collectors.groupingBy(n -> n.substring(Math.max(n.lastIndexOf('.'), n.lastIndexOf('$')) + 1)));
        assertThat(bySimpleName.entrySet().stream().filter(e -> e.getValue().size() > 1).map(Map.Entry::getValue).toList())
                .as("DTO records with the same name")
                .isEmpty();
    }
}
