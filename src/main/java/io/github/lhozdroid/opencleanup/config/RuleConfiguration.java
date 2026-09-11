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

    /**
     * Returns the value of a named rule option.
     *
     * @param optionName the option name to find
     * @return the configured option value, or {@code null} when it is not configured
     */
    public String optionValue(String optionName) {
        return options.stream()
                .filter(option -> optionName.equals(option.getName()))
                .map(RuleOption::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks whether a named rule option is configured as {@code true}.
     *
     * @param optionName the option name to check
     * @return {@code true} when the option value is case-insensitively {@code true}
     */
    public boolean isOptionEnabled(String optionName) {
        return "true".equalsIgnoreCase(optionValue(optionName));
    }
}
