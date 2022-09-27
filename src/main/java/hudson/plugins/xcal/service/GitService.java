/*
 * Copyright (C) 2019-2020  XC Software (Shenzhen) Ltd.
 *
 */

package hudson.plugins.xcal.service;


import hudson.model.TaskListener;
import hudson.plugins.xcal.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;


@Slf4j
@Service
public class GitService {

    private final TaskListener listener;

    public GitService(TaskListener listener) {
        this.listener = listener;
    }

    public String getLatestCommitId(File file, String repoName, String branch) {
        String commitId = null;
        try (Git git = Git.open(file)) {
            git.fetch().call();
            if (StringUtils.isEmpty(repoName)) {
                repoName = "origin";
            }
            String revString = "refs/remotes/" + repoName + "/" + branch;
            listener.getLogger().println(CommonUtil.formatString("[getLatestCommitId] revString:{} ", revString));
            git.fetch().call();
            for (RevCommit revCommit : git.log().add(git.getRepository().resolve(revString)).call()) {
                commitId = revCommit.getId().getName();
                if (StringUtils.isNotBlank(commitId)) {
                    listener.getLogger().println(CommonUtil.formatString("[getLastCommitId] Commit id retrieved: {}", commitId));
                    break;
                }
            }
        } catch (IOException | GitAPIException e) {
            listener.getLogger().println(CommonUtil.formatString("[getLastCommitId] Commit id is not available, {}: {} ", e.getClass(), e.getMessage()));
        }
        return commitId;
    }
}
