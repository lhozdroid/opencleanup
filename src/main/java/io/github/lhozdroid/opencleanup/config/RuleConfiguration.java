package io.github.lhozdroid.opencleanup.config;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Maven configuration for one cleanup rule or rule group.
 */
@Data
@NoArgsConstructor
public class RuleConfiguration {

    private String id;
    private boolean enabled = true;
    private List<RuleOption> options = new ArrayList<>();

    public String optionValue(String optionName) {
        return options.stream()
                .filter(option -> optionName.equals(option.getName()))
                .map(RuleOption::getValue)
                .findFirst()
                .orElse(null);
    }

    public boolean isOptionEnabled(String optionName) {
        return "true".equalsIgnoreCase(optionValue(optionName));
    }
}
