package io.github.lhozdroid.opencleanup.rule;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;
import io.github.lhozdroid.opencleanup.rewrite.SourceRewriteRule;

/**
 * Resolves configured rule IDs to the rules implemented by this plugin.
 */
public final class CleanupRuleRegistry {

    private static final String CODE_ORGANIZING_GROUP_ID = "code-organizing";
    private static final String CODE_STYLE_GROUP_ID = "code-style";
    private static final String DUPLICATE_CODE_GROUP_ID = "duplicate-code";
    private static final String JAVA_FEATURES_GROUP_ID = "java-features";
    private static final String MEMBER_ACCESSES_GROUP_ID = "member-accesses";
    private static final String MISSING_CODE_GROUP_ID = "missing-code";
    private static final String PERFORMANCE_GROUP_ID = "performance";
    private static final String SOURCE_FIXING_GROUP_ID = "source-fixing";
    private static final String UNNECESSARY_CODE_GROUP_ID = "unnecessary-code";

    private final Map<String, CleanupRule> rules;
    private final Map<String, SourceRewriteRule> sourceRules;

    /**
     * Creates a registry containing the cleanup rules implemented by the plugin.
     */
    public CleanupRuleRegistry() {
        this.rules = Map.ofEntries(
                Map.entry(ArrayCreationRule.ID, new ArrayCreationRule()),
                Map.entry(ArrayInitializerRule.ID, new ArrayInitializerRule()),
                Map.entry(ArraysFillRule.ID, new ArraysFillRule()),
                Map.entry(AutoboxingRule.ID, new AutoboxingRule()),
                Map.entry(BitwiseCheckSignRule.ID, new BitwiseCheckSignRule()),
                Map.entry(BooleanLiteralRule.ID, new BooleanLiteralRule()),
                Map.entry(BooleanValueRatherThanComparisonRule.ID, new BooleanValueRatherThanComparisonRule()),
                Map.entry(BoxingValueOfRule.ID, new BoxingValueOfRule()),
                Map.entry(BufferToBuilderRule.ID, new BufferToBuilderRule()),
                Map.entry(CollectionCloningRule.ID, new CollectionCloningRule()),
                Map.entry(ComparatorCriteriaRule.ID, new ComparatorCriteriaRule()),
                Map.entry(ConditionalReturnRule.ID, new ConditionalReturnRule()),
                Map.entry(ControlFlowMergeRule.ID, new ControlFlowMergeRule()),
                Map.entry(ControlStatementBlocksRule.ID, new ControlStatementBlocksRule()),
                Map.entry(DeprecatedAnnotationRule.ID, new DeprecatedAnnotationRule()),
                Map.entry(DeprecatedReplaceFieldRule.ID, new DeprecatedReplaceFieldRule()),
                Map.entry(DeprecatedReplaceMethodRule.ID, new DeprecatedReplaceMethodRule()),
                Map.entry(DoubleNegationRule.ID, new DoubleNegationRule()),
                Map.entry(ElseIfRule.ID, new ElseIfRule()),
                Map.entry(EmbeddedIfRule.ID, new EmbeddedIfRule()),
                Map.entry(EnhancedForRule.ID, new EnhancedForRule()),
                Map.entry(EvaluateNullableRule.ID, new EvaluateNullableRule()),
                Map.entry(ExtractIncrementRule.ID, new ExtractIncrementRule()),
                Map.entry(FieldsSingleUseRule.ID, new FieldsSingleUseRule()),
                Map.entry(UnusedImportsRule.ID, new UnusedImportsRule()),
                Map.entry(FunctionalInterfacesConvertRule.ID, new FunctionalInterfacesConvertRule()),
                Map.entry(HashModernizeRule.ID, new HashModernizeRule()),
                Map.entry(InstanceofPatternMatchingRule.ID, new InstanceofPatternMatchingRule()),
                Map.entry(InstanceofRule.ID, new InstanceofRule()),
                Map.entry(InstanceofToSwitchRule.ID, new InstanceofToSwitchRule()),
                Map.entry(InvertEqualsRule.ID, new InvertEqualsRule()),
                Map.entry(LambdaMethodReferenceRule.ID, new LambdaMethodReferenceRule()),
                Map.entry(LoopsBreakRule.ID, new LoopsBreakRule()),
                Map.entry(MapCloningRule.ID, new MapCloningRule()),
                Map.entry(MapMethodRule.ID, new MapMethodRule()),
                Map.entry(MembersSortRule.ID, new MembersSortRule()),
                Map.entry(MergeConditionalRule.ID, new MergeConditionalRule()),
                Map.entry(MissingAnnotationsRule.ID, new MissingAnnotationsRule()),
                Map.entry(ModuleImportsRule.ID, new ModuleImportsRule()),
                Map.entry(MultiCatchRule.ID, new MultiCatchRule()),
                Map.entry(NegationPushDownRule.ID, new NegationPushDownRule()),
                Map.entry(NoStringCreationRule.ID, new NoStringCreationRule()),
                Map.entry(NonStaticFieldsRule.ID, new NonStaticFieldsRule()),
                Map.entry(NonStaticMethodsRule.ID, new NonStaticMethodsRule()),
                Map.entry(NumberLiteralSuffixRule.ID, new NumberLiteralSuffixRule()),
                Map.entry(ObjectsEqualsRule.ID, new ObjectsEqualsRule()),
                Map.entry(OneIfForFallThroughRule.ID, new OneIfForFallThroughRule()),
                Map.entry(OperandFactorizationRule.ID, new OperandFactorizationRule()),
                Map.entry(OperatorsLazyLogicalRule.ID, new OperatorsLazyLogicalRule()),
                Map.entry(OrganizeImportsRule.ID, new OrganizeImportsRule()),
                Map.entry(OverriddenAssignmentRule.ID, new OverriddenAssignmentRule()),
                Map.entry(OverrideAnnotationRule.ID, new OverrideAnnotationRule()),
                Map.entry(OverrideInterfaceAnnotationRule.ID, new OverrideInterfaceAnnotationRule()),
                Map.entry(ParenthesesRule.ID, new ParenthesesRule()),
                Map.entry(PrimitiveComparisonRule.ID, new PrimitiveComparisonRule()),
                Map.entry(PrimitiveParsingRule.ID, new PrimitiveParsingRule()),
                Map.entry(PrimitiveRatherThanWrapperRule.ID, new PrimitiveRatherThanWrapperRule()),
                Map.entry(PrimitiveSerializationRule.ID, new PrimitiveSerializationRule()),
                Map.entry(PullOutIfRule.ID, new PullOutIfRule()),
                Map.entry(PullUpAssignmentRule.ID, new PullUpAssignmentRule()),
                Map.entry(ReduceIndentationRule.ID, new ReduceIndentationRule()),
                Map.entry(RedundantComparatorRule.ID, new RedundantComparatorRule()),
                Map.entry(RedundantComparisonRule.ID, new RedundantComparisonRule()),
                Map.entry(RedundantFallThroughEndRule.ID, new RedundantFallThroughEndRule()),
                Map.entry(RedundantIfConditionRule.ID, new RedundantIfConditionRule()),
                Map.entry(RedundantModifiersRule.ID, new RedundantModifiersRule()),
                Map.entry(RedundantSemicolonRule.ID, new RedundantSemicolonRule()),
                Map.entry(RedundantSubstringArgumentRule.ID, new RedundantSubstringArgumentRule()),
                Map.entry(RedundantSuperCallRule.ID, new RedundantSuperCallRule()),
                Map.entry(RedundantTypeArgumentsRule.ID, new RedundantTypeArgumentsRule()),
                Map.entry(RegexPrecompileRule.ID, new RegexPrecompileRule()),
                Map.entry(SerialVersionUidRule.ID, new SerialVersionUidRule()),
                Map.entry(SimplifyBooleanIfElseRule.ID, new SimplifyBooleanIfElseRule()),
                Map.entry(SimplifyLambdaRule.ID, new SimplifyLambdaRule()),
                Map.entry(StandardizeComparisonRule.ID, new StandardizeComparisonRule()),
                Map.entry(StaticInnerRule.ID, new StaticInnerRule()),
                Map.entry(StaticMembersRule.ID, new StaticMembersRule()),
                Map.entry(StrictlyEqualOrDifferentRule.ID, new StrictlyEqualOrDifferentRule()),
                Map.entry(StringsIsBlankRule.ID, new StringsIsBlankRule()),
                Map.entry(StringsJoinRule.ID, new StringsJoinRule()),
                Map.entry(StringsPlainReplacementRule.ID, new StringsPlainReplacementRule()),
                Map.entry(StringsStringBuilderRule.ID, new StringsStringBuilderRule()),
                Map.entry(SuppressWarningsRule.ID, new SuppressWarningsRule()),
                Map.entry(SwitchExpressionsRule.ID, new SwitchExpressionsRule()),
                Map.entry(SystemPropertyConstantsRule.ID, new SystemPropertyConstantsRule()),
                Map.entry(TernaryOperatorRule.ID, new TernaryOperatorRule()),
                Map.entry(TryWithResourcesRule.ID, new TryWithResourcesRule()),
                Map.entry(UnboxingRule.ID, new UnboxingRule()),
                Map.entry(UnimplementedMethodsRule.ID, new UnimplementedMethodsRule()),
                Map.entry(UnloopedWhileRule.ID, new UnloopedWhileRule()),
                Map.entry(UnnecessaryCastRule.ID, new UnnecessaryCastRule()),
                Map.entry(UnreachableBlockRule.ID, new UnreachableBlockRule()),
                Map.entry(UnusedPrivateMembersRule.ID, new UnusedPrivateMembersRule()),
                Map.entry(UseAddAllRule.ID, new UseAddAllRule()),
                Map.entry(UseSwitchRule.ID, new UseSwitchRule()),
                Map.entry(UselessContinueRule.ID, new UselessContinueRule()),
                Map.entry(UselessReturnRule.ID, new UselessReturnRule()),
                Map.entry(VarDeclarationsRule.ID, new VarDeclarationsRule()),
                Map.entry(VariableDeclarationsFinalRule.ID, new VariableDeclarationsFinalRule()));
        this.sourceRules = Map.ofEntries(
                Map.entry(FormatSourceRule.ID, new FormatSourceRule()),
                Map.entry(TrailingWhitespaceRule.ID, new TrailingWhitespaceRule()),
                Map.entry(IndentationRule.ID, new IndentationRule()));
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
                addIfEnabled(selected, configuration, UnusedPrivateMembersRule.ID);
                addIfEnabled(selected, configuration, SuppressWarningsRule.ID);
                addIfEnabled(selected, configuration, UnnecessaryCastRule.ID);
                addIfEnabled(selected, configuration, ArraysFillRule.ID);
                addIfEnabled(selected, configuration, EvaluateNullableRule.ID);
                addIfEnabled(selected, configuration, RedundantComparisonRule.ID);
                addIfEnabled(selected, configuration, UnreachableBlockRule.ID);
                addIfEnabled(selected, configuration, MapMethodRule.ID);
                addIfEnabled(selected, configuration, CollectionCloningRule.ID);
                addIfEnabled(selected, configuration, MapCloningRule.ID);
                addIfEnabled(selected, configuration, OverriddenAssignmentRule.ID);
                addIfEnabled(selected, configuration, RedundantComparatorRule.ID);
                addIfEnabled(selected, configuration, ArrayCreationRule.ID);
                addIfEnabled(selected, configuration, UnloopedWhileRule.ID);
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
                addIfEnabled(selected, configuration, RedundantModifiersRule.ID);
                addIfEnabled(selected, configuration, RedundantSubstringArgumentRule.ID);
                continue;
            }

            if (CODE_ORGANIZING_GROUP_ID.equals(configuration.getId())) {
                addIfEnabled(selected, configuration, OrganizeImportsRule.ID);
                addIfEnabled(selected, configuration, MembersSortRule.ID);
                continue;
            }

            if (CODE_STYLE_GROUP_ID.equals(configuration.getId())) {
                addIfOptionPresent(selected, configuration, ControlStatementBlocksRule.ID);
                addIfEnabled(selected, configuration, ElseIfRule.ID);
                addIfEnabled(selected, configuration, SimplifyBooleanIfElseRule.ID);
                addIfEnabled(selected, configuration, ReduceIndentationRule.ID);
                addIfEnabled(selected, configuration, UseSwitchRule.ID);
                addIfEnabled(selected, configuration, UseAddAllRule.ID);
                addIfOptionPresent(selected, configuration, ParenthesesRule.ID);
                addIfEnabled(selected, configuration, ExtractIncrementRule.ID);
                addIfEnabled(selected, configuration, PullUpAssignmentRule.ID);
                addIfEnabled(selected, configuration, InstanceofRule.ID);
                addIfEnabled(selected, configuration, NumberLiteralSuffixRule.ID);
                addIfAnyOptionEnabled(
                        selected,
                        configuration,
                        VariableDeclarationsFinalRule.ID,
                        VariableDeclarationsFinalRule.FIELDS,
                        VariableDeclarationsFinalRule.PARAMETERS,
                        VariableDeclarationsFinalRule.LOCALS);
                addIfEnabled(selected, configuration, LambdaMethodReferenceRule.ID);
                continue;
            }

            if (JAVA_FEATURES_GROUP_ID.equals(configuration.getId())) {
                addIfEnabled(selected, configuration, InstanceofPatternMatchingRule.ID);
                addIfEnabled(selected, configuration, EnhancedForRule.ID);
                addIfEnabled(selected, configuration, ModuleImportsRule.ID);
                addIfEnabled(selected, configuration, InstanceofToSwitchRule.ID);
                addIfEnabled(selected, configuration, SwitchExpressionsRule.ID);
                addIfEnabled(selected, configuration, VarDeclarationsRule.ID);
                addIfOptionPresent(selected, configuration, FunctionalInterfacesConvertRule.ID);
                addIfEnabled(selected, configuration, SimplifyLambdaRule.ID);
                addIfEnabled(selected, configuration, ComparatorCriteriaRule.ID);
                addIfEnabled(selected, configuration, StringsJoinRule.ID);
                addIfEnabled(selected, configuration, TryWithResourcesRule.ID);
                addIfEnabled(selected, configuration, MultiCatchRule.ID);
                addIfEnabled(selected, configuration, RedundantTypeArgumentsRule.ID);
                addIfEnabled(selected, configuration, HashModernizeRule.ID);
                addIfEnabled(selected, configuration, ObjectsEqualsRule.ID);
                addIfEnabled(selected, configuration, SystemPropertyConstantsRule.ID);
                addIfEnabled(selected, configuration, AutoboxingRule.ID);
                addIfEnabled(selected, configuration, UnboxingRule.ID);
                continue;
            }

            if (DUPLICATE_CODE_GROUP_ID.equals(configuration.getId())) {
                addIfEnabled(selected, configuration, OperandFactorizationRule.ID);
                addIfEnabled(selected, configuration, TernaryOperatorRule.ID);
                addIfEnabled(selected, configuration, StrictlyEqualOrDifferentRule.ID);
                addIfEnabled(selected, configuration, MergeConditionalRule.ID);
                addIfEnabled(selected, configuration, ControlFlowMergeRule.ID);
                addIfEnabled(selected, configuration, OneIfForFallThroughRule.ID);
                addIfEnabled(selected, configuration, RedundantFallThroughEndRule.ID);
                addIfEnabled(selected, configuration, RedundantIfConditionRule.ID);
                addIfEnabled(selected, configuration, PullOutIfRule.ID);
                continue;
            }

            if (MEMBER_ACCESSES_GROUP_ID.equals(configuration.getId())) {
                addIfOptionPresent(selected, configuration, NonStaticFieldsRule.ID);
                addIfOptionPresent(selected, configuration, NonStaticMethodsRule.ID);
                addIfEnabled(selected, configuration, StaticMembersRule.ID);
                continue;
            }

            if (MISSING_CODE_GROUP_ID.equals(configuration.getId())) {
                addIfEnabled(selected, configuration, MissingAnnotationsRule.ID);
                addIfEnabled(selected, configuration, OverrideAnnotationRule.ID);
                addIfEnabled(selected, configuration, OverrideInterfaceAnnotationRule.ID);
                addIfEnabled(selected, configuration, DeprecatedAnnotationRule.ID);
                addIfOptionPresent(selected, configuration, SerialVersionUidRule.ID);
                addIfEnabled(selected, configuration, UnimplementedMethodsRule.ID);
                continue;
            }

            if (PERFORMANCE_GROUP_ID.equals(configuration.getId())) {
                addIfEnabled(selected, configuration, BooleanLiteralRule.ID);
                addIfEnabled(selected, configuration, StringsIsBlankRule.ID);
                addIfEnabled(selected, configuration, FieldsSingleUseRule.ID);
                addIfEnabled(selected, configuration, LoopsBreakRule.ID);
                addIfEnabled(selected, configuration, StaticInnerRule.ID);
                addIfEnabled(selected, configuration, StringsStringBuilderRule.ID);
                addIfEnabled(selected, configuration, StringsPlainReplacementRule.ID);
                addIfEnabled(selected, configuration, OperatorsLazyLogicalRule.ID);
                addIfEnabled(selected, configuration, BoxingValueOfRule.ID);
                addIfEnabled(selected, configuration, PrimitiveComparisonRule.ID);
                addIfEnabled(selected, configuration, PrimitiveParsingRule.ID);
                addIfEnabled(selected, configuration, PrimitiveSerializationRule.ID);
                addIfEnabled(selected, configuration, PrimitiveRatherThanWrapperRule.ID);
                addIfEnabled(selected, configuration, RegexPrecompileRule.ID);
                addIfEnabled(selected, configuration, BufferToBuilderRule.ID);
                addIfEnabled(selected, configuration, NoStringCreationRule.ID);
                continue;
            }

            if (SOURCE_FIXING_GROUP_ID.equals(configuration.getId())) {
                addIfEnabled(selected, configuration, InvertEqualsRule.ID);
                addIfEnabled(selected, configuration, StandardizeComparisonRule.ID);
                addIfEnabled(selected, configuration, BitwiseCheckSignRule.ID);
                addIfEnabled(selected, configuration, DeprecatedReplaceMethodRule.ID);
                addIfEnabled(selected, configuration, DeprecatedReplaceFieldRule.ID);
                continue;
            }

            CleanupRule rule = rules.get(configuration.getId());
            if (rule == null && sourceRules.containsKey(configuration.getId())) {
                continue;
            }
            if (rule == null) {
                throw new IllegalArgumentException(
                        "Unknown cleanup rule: " + configuration.getId());
            }
            add(selected, rule, configuration);
        }
        return List.copyOf(selected.values());
    }

    /**
     * Resolves enabled Maven configurations to unique source-level rules.
     *
     * @param configurations the configured rule groups and rules
     * @return the selected source rules in configuration order
     * @throws IllegalArgumentException when an enabled source rule is not implemented
     */
    public List<ConfiguredSourceRule> resolveSourceRules(List<RuleConfiguration> configurations) {
        Map<String, ConfiguredSourceRule> selected = new LinkedHashMap<>();
        for (RuleConfiguration configuration : configurations) {
            if (!configuration.isEnabled()) {
                continue;
            }
            if (CODE_ORGANIZING_GROUP_ID.equals(configuration.getId())) {
                addSourceIfConfigured(selected, configuration, FormatSourceRule.ID);
                addSourceIfConfigured(selected, configuration, TrailingWhitespaceRule.ID);
                addSourceIfConfigured(selected, configuration, IndentationRule.ID);
                continue;
            }
            SourceRewriteRule rule = sourceRules.get(configuration.getId());
            if (rule != null) {
                selected.putIfAbsent(rule.id(), new ConfiguredSourceRule(rule, configuration));
            }
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

    /**
     * Adds a known rule when its option is configured with any value.
     *
     * @param selected the selected rules indexed by rule identifier
     * @param configuration the group configuration containing the option
     * @param ruleId the rule identifier represented by the option
     */
    private void addIfOptionPresent(
            Map<String, ConfiguredRule> selected,
            RuleConfiguration configuration,
            String ruleId) {
        if (configuration.optionValue(ruleId) != null) {
            add(selected, rules.get(ruleId), configuration);
        }
    }

    /**
     * Adds a known rule when at least one of its named options is enabled.
     *
     * @param selected the selected rules indexed by rule identifier
     * @param configuration the group configuration containing the options
     * @param ruleId the rule identifier represented by the options
     * @param optionNames the option names that select the rule
     */
    private void addIfAnyOptionEnabled(
            Map<String, ConfiguredRule> selected,
            RuleConfiguration configuration,
            String ruleId,
            String... optionNames) {
        for (String optionName : optionNames) {
            if (configuration.isOptionEnabled(optionName)) {
                add(selected, rules.get(ruleId), configuration);
                return;
            }
        }
    }

    /**
     * Adds a source rule when its group option is enabled.
     *
     * @param selected the selected source rules indexed by rule identifier
     * @param configuration the group configuration containing the option
     * @param ruleId the source rule identifier represented by the option
     */
    private void addSourceIfEnabled(
            Map<String, ConfiguredSourceRule> selected,
            RuleConfiguration configuration,
            String ruleId) {
        if (!configuration.isOptionEnabled(ruleId)) {
            return;
        }
        SourceRewriteRule rule = sourceRules.get(ruleId);
        if (rule == null) {
            throw new IllegalArgumentException("Unknown source cleanup rule: " + ruleId);
        }
        selected.putIfAbsent(rule.id(), new ConfiguredSourceRule(rule, configuration));
    }

    /**
     * Adds a source rule when its group option has any configured value.
     *
     * @param selected the selected source rules indexed by rule identifier
     * @param configuration the group configuration containing the option
     * @param ruleId the source rule identifier represented by the option
     */
    private void addSourceIfConfigured(
            Map<String, ConfiguredSourceRule> selected,
            RuleConfiguration configuration,
            String ruleId) {
        boolean hasRuleOption = configuration.optionValue(ruleId) != null;
        boolean hasTrailingWhitespaceMode = TrailingWhitespaceRule.ID.equals(ruleId)
                && configuration.optionValue("mode") != null;
        if (!hasRuleOption && !hasTrailingWhitespaceMode) {
            return;
        }
        SourceRewriteRule rule = sourceRules.get(ruleId);
        if (rule == null) {
            throw new IllegalArgumentException("Unknown source cleanup rule: " + ruleId);
        }
        selected.putIfAbsent(rule.id(), new ConfiguredSourceRule(rule, configuration));
    }

    public record ConfiguredRule(CleanupRule rule, RuleConfiguration configuration) {
    }

    /**
     * A source-level cleanup rule paired with its Maven configuration.
     *
     * @param rule the source-level rule
     * @param configuration the Maven configuration
     */
    public record ConfiguredSourceRule(SourceRewriteRule rule, RuleConfiguration configuration) {
    }
}
