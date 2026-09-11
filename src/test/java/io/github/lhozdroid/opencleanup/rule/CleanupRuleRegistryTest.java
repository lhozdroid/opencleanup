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

        assertEquals(99, ((Map<?, ?>) astRules.get(registry)).size());
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
        RuleOption option = new RuleOption();
        option.setName(ruleId);
        option.setValue("true");

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(groupId);
        configuration.setOptions(List.of(option));
        return configuration;
    }
}
