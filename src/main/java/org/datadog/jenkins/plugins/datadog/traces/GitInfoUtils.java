package org.datadog.jenkins.plugins.datadog.traces;

public class GitInfoUtils {

    private GitInfoUtils() {}

    /**
     * Returns a normalized git tag
     * E.g: refs/heads/tags/0.1.0 or origin/tags/0.1.0 returns 0.1.0
     * @param tagName the tag name to normalize
     * @return normalized git tag
     */
    public static String normalizeTag(String tagName) {
        if(tagName == null || tagName.isEmpty() || !tagName.contains("tags")) {
            return null;
        }

        final String tagNameNoSlash = (tagName.startsWith("/")) ? tagName.replaceFirst("/", "") : tagName;
        return removeRefs(tagNameNoSlash).replace("tags/", "");
    }

    /**
     * Returns a normalized git branch
     * E.g. refs/heads/master or origin/master returns master
     * @param branchName the branch name to normalize
     * @return normalized git tag
     */
    public static String normalizeBranch(String branchName) {
        if(branchName == null || branchName.isEmpty() || branchName.contains("tags")) {
            return null;
        }

        final String branchNameNoSlash = (branchName.startsWith("/")) ? branchName.replaceFirst("/", "") : branchName;
        return removeRefs(branchNameNoSlash);
    }

    private static String removeRefs(String gitReference) {
        if(gitReference.startsWith("origin/")) {
            return gitReference.replace("origin/", "");
        } else if(gitReference.startsWith("refs/heads/")) {
            return gitReference.replace("refs/heads/", "");
        }
        return gitReference;
    }
}
