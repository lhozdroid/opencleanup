package io.github.lhozdroid.opencleanup.config;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A named option supplied for a cleanup rule.
 */
@Data
@NoArgsConstructor
public class RuleOption {

    private String name;
    private String value;
}
