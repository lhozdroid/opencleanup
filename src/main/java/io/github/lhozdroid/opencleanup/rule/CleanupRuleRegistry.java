package io.github.lhozdroid.opencleanup.rule;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Resolves configured rule IDs to the rules implemented by this plugin.
 */
public final class CleanupRuleRegistry {

    private static final String UNNECESSARY_CODE_GROUP_ID = "unnecessary-code";

    private final Map<String, CleanupRule> rules;

    public CleanupRuleRegistry() {
        this.rules = Map.of(UnusedImportsRule.ID, new UnusedImportsRule());
    }

    public List<ConfiguredRule> resolve(List<RuleConfiguration> configurations) {
        Map<String, ConfiguredRule> selected = new LinkedHashMap<>();
        for (RuleConfiguration configuration : configurations) {
            if (!configuration.isEnabled()) {
                continue;
            }

            if (UNNECESSARY_CODE_GROUP_ID.equals(configuration.getId())) {
                if (configuration.isOptionEnabled(UnusedImportsRule.ID)) {
                    add(selected, rules.get(UnusedImportsRule.ID), configuration);
                }
                continue;
            }

            CleanupRule rule = rules.get(configuration.getId());
            if (rule == null) {
                throw new IllegalArgumentException(
                        "Unknown or not-yet-implemented cleanup rule: " + configuration.getId());
            }
            add(selected, rule, configuration);
        }
        return List.copyOf(selected.values());
    }

    private void add(Map<String, ConfiguredRule> selected, CleanupRule rule, RuleConfiguration configuration) {
        selected.putIfAbsent(rule.id(), new ConfiguredRule(rule, configuration));
    }

    public record ConfiguredRule(CleanupRule rule, RuleConfiguration configuration) {
    }
}
