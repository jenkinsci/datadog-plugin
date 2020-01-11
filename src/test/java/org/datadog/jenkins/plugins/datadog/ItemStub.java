package org.datadog.jenkins.plugins.datadog;

import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.search.Search;
import hudson.search.SearchIndex;
import hudson.security.ACL;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class ItemStub implements Item {
    @Override
    public ItemGroup<? extends Item> getParent() {
        return null;
    }

    @Override
    public Collection<? extends Job> getAllJobs() {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public String getFullName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getFullDisplayName() {
        return null;
    }

    @Override
    public String getUrl() {
        return null;
    }

    @Override
    public String getShortUrl() {
        return null;
    }

    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {

    }

    @Override
    public void onCopiedFrom(Item src) {

    }

    @Override
    public void save() throws IOException {

    }

    @Override
    public void delete() throws IOException, InterruptedException {

    }

    @Override
    public File getRootDir() {
        return null;
    }

    @Override
    public Search getSearch() {
        return null;
    }

    @Override
    public String getSearchName() {
        return null;
    }

    @Override
    public String getSearchUrl() {
        return null;
    }

    @Override
    public SearchIndex getSearchIndex() {
        return null;
    }

    @Nonnull
    @Override
    public ACL getACL() {
        return null;
    }
}
