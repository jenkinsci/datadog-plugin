package org.datadog.jenkins.plugins.datadog.traces;

import java.net.URI;
import java.net.URISyntaxException;

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
        } else if(gitReference.startsWith("refs/remotes/")) {
            // find the next slash after remotes/ to trim remote name ("origin" or anything else)
            int idx = gitReference.indexOf('/', "refs/remotes/".length());
            return idx >= 0 ? gitReference.substring(idx + 1) : gitReference.substring("refs/remotes/".length());
        }
        return gitReference;
    }

    /**
     * Filters the user info given a valid HTTP URL.
     * @param urlStr input URL
     * @return URL without user info.
     */
    public static String filterSensitiveInfo(String urlStr) {
        if (urlStr == null || urlStr.isEmpty()) {
            return urlStr;
        }

        try {
            final URI url = new URI(urlStr);
            final String userInfo = url.getRawUserInfo();
            return urlStr.replace(userInfo + "@", "");
        } catch (final URISyntaxException ex) {
            return urlStr;
        }
    }

    public static boolean isSha(String refName) {
        if (refName == null || refName.length() != 40) {
            return false;
        }
        for (int i = 0; i < refName.length(); i++) {
            char c = refName.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }
}
