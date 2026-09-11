package io.github.lhozdroid.opencleanup.rewrite;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/**
 * Summary of one rewrite execution.
 */
@Value
@Builder
public class RewriteReport {

    int filesVisited;
    int filesChanged;
    @Singular("changedFile")
    List<Path> changedFiles;
    @Singular("appliedRule")
    Set<String> appliedRules;
}
