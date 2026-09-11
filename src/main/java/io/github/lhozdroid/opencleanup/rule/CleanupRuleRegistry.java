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

    /**
     * Creates a registry containing the cleanup rules implemented by the plugin.
     */
    public CleanupRuleRegistry() {
        this.rules = Map.of(
                UnusedImportsRule.ID, new UnusedImportsRule(),
                BooleanValueRatherThanComparisonRule.ID, new BooleanValueRatherThanComparisonRule(),
                UselessReturnRule.ID, new UselessReturnRule(),
                RedundantSuperCallRule.ID, new RedundantSuperCallRule(),
                DoubleNegationRule.ID, new DoubleNegationRule(),
                UselessContinueRule.ID, new UselessContinueRule(),
                OrganizeImportsRule.ID, new OrganizeImportsRule(),
                ElseIfRule.ID, new ElseIfRule());
    }

    /**
     * Resolves enabled Maven configurations to unique executable rules.
     *
     * @param configurations the configured rule groups and rules
     * @return the selected rules in configuration order
     * @throws IllegalArgumentException when an enabled rule is not implemented
     */
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

    /**
     * Adds a rule to the selected set unless it has already been selected.
     *
     * @param selected the selected rules indexed by rule identifier
     * @param rule the rule to add
     * @param configuration the configuration associated with the rule
     */
    private void add(Map<String, ConfiguredRule> selected, CleanupRule rule, RuleConfiguration configuration) {
        selected.putIfAbsent(rule.id(), new ConfiguredRule(rule, configuration));
    }

    public record ConfiguredRule(CleanupRule rule, RuleConfiguration configuration) {
    }
}
