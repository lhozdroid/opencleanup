package io.github.lhozdroid.opencleanup.rule;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Resolves configured rule IDs to the rules implemented by this plugin.
 */
public final class CleanupRuleRegistry {

    private static final String CODE_ORGANIZING_GROUP_ID = "code-organizing";
    private static final String CODE_STYLE_GROUP_ID = "code-style";
    private static final String JAVA_FEATURES_GROUP_ID = "java-features";
    private static final String PERFORMANCE_GROUP_ID = "performance";
    private static final String SOURCE_FIXING_GROUP_ID = "source-fixing";
    private static final String UNNECESSARY_CODE_GROUP_ID = "unnecessary-code";

    private final Map<String, CleanupRule> rules;

    /**
     * Creates a registry containing the cleanup rules implemented by the plugin.
     */
    public CleanupRuleRegistry() {
        this.rules = Map.ofEntries(
                Map.entry(UnusedImportsRule.ID, new UnusedImportsRule()),
                Map.entry(BooleanValueRatherThanComparisonRule.ID, new BooleanValueRatherThanComparisonRule()),
                Map.entry(UselessReturnRule.ID, new UselessReturnRule()),
                Map.entry(RedundantSuperCallRule.ID, new RedundantSuperCallRule()),
                Map.entry(DoubleNegationRule.ID, new DoubleNegationRule()),
                Map.entry(UselessContinueRule.ID, new UselessContinueRule()),
                Map.entry(OrganizeImportsRule.ID, new OrganizeImportsRule()),
                Map.entry(ElseIfRule.ID, new ElseIfRule()),
                Map.entry(SimplifyBooleanIfElseRule.ID, new SimplifyBooleanIfElseRule()),
                Map.entry(BooleanLiteralRule.ID, new BooleanLiteralRule()),
                Map.entry(InvertEqualsRule.ID, new InvertEqualsRule()),
                Map.entry(NegationPushDownRule.ID, new NegationPushDownRule()),
                Map.entry(RedundantSemicolonRule.ID, new RedundantSemicolonRule()),
                Map.entry(ArrayInitializerRule.ID, new ArrayInitializerRule()),
                Map.entry(ConditionalReturnRule.ID, new ConditionalReturnRule()),
                Map.entry(ControlStatementBlocksRule.ID, new ControlStatementBlocksRule()),
                Map.entry(EmbeddedIfRule.ID, new EmbeddedIfRule()),
                Map.entry(InstanceofPatternMatchingRule.ID, new InstanceofPatternMatchingRule()),
                Map.entry(ParenthesesRule.ID, new ParenthesesRule()),
                Map.entry(StandardizeComparisonRule.ID, new StandardizeComparisonRule()));
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
                addIfEnabled(selected, configuration, UnusedImportsRule.ID);
                addIfEnabled(selected, configuration, BooleanValueRatherThanComparisonRule.ID);
                addIfEnabled(selected, configuration, DoubleNegationRule.ID);
                addIfEnabled(selected, configuration, RedundantSuperCallRule.ID);
                addIfEnabled(selected, configuration, UselessReturnRule.ID);
                addIfEnabled(selected, configuration, UselessContinueRule.ID);
                addIfEnabled(selected, configuration, NegationPushDownRule.ID);
                addIfEnabled(selected, configuration, RedundantSemicolonRule.ID);
                addIfEnabled(selected, configuration, ArrayInitializerRule.ID);
                addIfEnabled(selected, configuration, ConditionalReturnRule.ID);
                addIfEnabled(selected, configuration, EmbeddedIfRule.ID);
                continue;
            }

            if (CODE_ORGANIZING_GROUP_ID.equals(configuration.getId())) {
                addIfEnabled(selected, configuration, OrganizeImportsRule.ID);
                continue;
            }

            if (CODE_STYLE_GROUP_ID.equals(configuration.getId())) {
                addIfOptionValue(selected, configuration, ControlStatementBlocksRule.ID, "always");
                addIfEnabled(selected, configuration, ElseIfRule.ID);
                addIfEnabled(selected, configuration, SimplifyBooleanIfElseRule.ID);
                addIfOptionValue(selected, configuration, ParenthesesRule.ID, "never");
                continue;
            }

            if (JAVA_FEATURES_GROUP_ID.equals(configuration.getId())) {
                addIfEnabled(selected, configuration, InstanceofPatternMatchingRule.ID);
                continue;
            }

            if (PERFORMANCE_GROUP_ID.equals(configuration.getId())) {
                addIfEnabled(selected, configuration, BooleanLiteralRule.ID);
                continue;
            }

            if (SOURCE_FIXING_GROUP_ID.equals(configuration.getId())) {
                addIfEnabled(selected, configuration, InvertEqualsRule.ID);
                addIfEnabled(selected, configuration, StandardizeComparisonRule.ID);
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

    /**
     * Adds a known rule when its group option is enabled.
     *
     * @param selected the selected rules indexed by rule identifier
     * @param configuration the group configuration containing the option
     * @param ruleId the rule identifier represented by the option
     */
    private void addIfEnabled(
            Map<String, ConfiguredRule> selected,
            RuleConfiguration configuration,
            String ruleId) {
        if (configuration.isOptionEnabled(ruleId)) {
            add(selected, rules.get(ruleId), configuration);
        }
    }

    /**
     * Adds a known rule when its group option has the required value.
     *
     * @param selected the selected rules indexed by rule identifier
     * @param configuration the group configuration containing the option
     * @param ruleId the rule identifier represented by the option
     * @param expectedValue the option value that this implementation supports
     */
    private void addIfOptionValue(
            Map<String, ConfiguredRule> selected,
            RuleConfiguration configuration,
            String ruleId,
            String expectedValue) {
        if (expectedValue.equalsIgnoreCase(configuration.optionValue(ruleId))) {
            add(selected, rules.get(ruleId), configuration);
        }
    }

    public record ConfiguredRule(CleanupRule rule, RuleConfiguration configuration) {
    }
}
