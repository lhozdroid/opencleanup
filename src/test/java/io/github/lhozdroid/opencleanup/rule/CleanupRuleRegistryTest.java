package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;
import io.github.lhozdroid.opencleanup.config.RuleOption;

class CleanupRuleRegistryTest {

    /**
     * Verifies implemented rules can be selected through their Eclipse-style groups.
     */
    @Test
    void resolvesImplementedRulesFromGroups() {
        RuleConfiguration codeStyle = configuration(
                "code-style", ElseIfRule.ID);
        RuleConfiguration codeOrganizing = configuration(
                "code-organizing", OrganizeImportsRule.ID);
        RuleConfiguration performance = configuration(
                "performance", BooleanLiteralRule.ID);
        RuleConfiguration sourceFixing = configuration(
                "source-fixing", InvertEqualsRule.ID);
        RuleConfiguration javaFeatures = configuration(
                "java-features", InstanceofPatternMatchingRule.ID);
        RuleConfiguration unnecessaryCode = configuration(
                "unnecessary-code", DoubleNegationRule.ID);

        List<CleanupRuleRegistry.ConfiguredRule> rules = new CleanupRuleRegistry()
                .resolve(List.of(
                        codeStyle,
                        codeOrganizing,
                        performance,
                        sourceFixing,
                        javaFeatures,
                        unnecessaryCode));

        assertEquals(List.of(
                ElseIfRule.ID,
                OrganizeImportsRule.ID,
                BooleanLiteralRule.ID,
                InvertEqualsRule.ID,
                InstanceofPatternMatchingRule.ID,
                DoubleNegationRule.ID),
                rules.stream().map(configured -> configured.rule().id()).toList());
    }

    /**
     * Verifies disabled groups do not select any of their options.
     */
    @Test
    void ignoresDisabledGroups() {
        RuleConfiguration codeStyle = configuration("code-style", ElseIfRule.ID);
        codeStyle.setEnabled(false);

        assertEquals(List.of(), new CleanupRuleRegistry().resolve(List.of(codeStyle)));
    }

    /**
     * Verifies that simple type-name cleanup is selectable from the code-organizing group.
     */
    @Test
    void resolvesSimpleTypeNameCleanupFromCodeOrganizingGroup() {
        RuleConfiguration codeOrganizing = configuration(
                "code-organizing", UseSimpleTypeNamesRule.ID);

        assertEquals(
                List.of(UseSimpleTypeNamesRule.ID),
                new CleanupRuleRegistry()
                        .resolve(List.of(codeOrganizing))
                        .stream()
                        .map(configured -> configured.rule().id())
                        .toList());
    }

    /**
     * Verifies false-valued source options do not accidentally select source rules.
     */
    @Test
    void ignoresDisabledSourceOptionsInCodeOrganizingGroup() {
        RuleConfiguration codeOrganizing = new RuleConfiguration();
        codeOrganizing.setId("code-organizing");
        codeOrganizing.setOptions(List.of(
                option(FormatSourceRule.ID, "false"),
                option(TrailingWhitespaceRule.ID, "false"),
                option(IndentationRule.ID, "false")));

        assertEquals(
                List.of(),
                new CleanupRuleRegistry().resolveSourceRules(List.of(codeOrganizing)));
    }

    /**
     * Verifies unsupported trailing-whitespace selectors and modes do not enable cleanup.
     */
    @Test
    void ignoresInvalidTrailingWhitespaceOptions() {
        RuleConfiguration codeOrganizing = new RuleConfiguration();
        codeOrganizing.setId("code-organizing");
        codeOrganizing.setOptions(List.of(
                option(TrailingWhitespaceRule.ID, "garbage"),
                option("mode", "garbage")));

        assertEquals(
                List.of(),
                new CleanupRuleRegistry().resolveSourceRules(List.of(codeOrganizing)));
    }

    /**
     * Verifies the documented mode-only selection enables trailing-whitespace cleanup.
     */
    @Test
    void resolvesTrailingWhitespaceModeWithoutSelector() {
        RuleConfiguration codeOrganizing = new RuleConfiguration();
        codeOrganizing.setId("code-organizing");
        codeOrganizing.setOptions(List.of(option("mode", "ignore-empty-lines")));

        assertEquals(
                List.of(TrailingWhitespaceRule.ID),
                new CleanupRuleRegistry().resolveSourceRules(List.of(codeOrganizing))
                        .stream()
                        .map(configured -> configured.rule().id())
                        .toList());
    }

    /**
     * Verifies that the full documented catalog is present in the AST and source rule registries.
     *
     * @throws ReflectiveOperationException if the registry fields cannot be inspected
     */
    @Test
    void registersCompleteRuleCatalog() throws ReflectiveOperationException {
        CleanupRuleRegistry registry = new CleanupRuleRegistry();
        Field astRules = CleanupRuleRegistry.class.getDeclaredField("rules");
        Field sourceRules = CleanupRuleRegistry.class.getDeclaredField("sourceRules");
        astRules.setAccessible(true);
        sourceRules.setAccessible(true);

        assertEquals(100, ((Map<?, ?>) astRules.get(registry)).size());
        assertEquals(3, ((Map<?, ?>) sourceRules.get(registry)).size());
    }

    /**
     * Creates a group configuration with one enabled option.
     *
     * @param groupId the group identifier
     * @param ruleId the enabled option identifier
     * @return the configured rule group
     */
    private RuleConfiguration configuration(String groupId, String ruleId) {
        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(groupId);
        configuration.setOptions(List.of(option(ruleId, "true")));
        return configuration;
    }

    /**
     * Creates one named rule option.
     *
     * @param name the option name
     * @param value the option value
     * @return the configured option
     */
    private RuleOption option(String name, String value) {
        RuleOption option = new RuleOption();
        option.setName(name);
        option.setValue(value);
        return option;
    }
}
