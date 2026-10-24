package com.copilot.git;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Clones the target repository into a temporary work directory.
 * Shallow clone (depth 1) of one branch: we only need the current source tree.
 */
@Slf4j
@Service
public class GitCloneService {

    private final Path workdir;

    public GitCloneService(@Value("${copilot.workdir}") String workdir) {
        this.workdir = Path.of(workdir);
    }

    public record CloneResult(Path dir, String commitId, String branch) {}

    public CloneResult clone(String repoUrl, String branch, String token) throws Exception {
        Files.createDirectories(workdir);
        Path target = workdir.resolve(UUID.randomUUID().toString());
        String useBranch = (branch == null || branch.isBlank()) ? "main" : branch;

        log.info("Cloning {} ({}) into {}", repoUrl, useBranch, target);

        var cmd = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(target.toFile())
                .setBranch("refs/heads/" + useBranch)
                .setDepth(1)
                .setCloneAllBranches(false);

        if (token != null && !token.isBlank()) {
            // GitHub / GitLab / Bitbucket all accept a token as the password with any user name.
            cmd.setCredentialsProvider(new UsernamePasswordCredentialsProvider("x-access-token", token));
        }

        try (Git git = cmd.call()) {
            ObjectId head = git.getRepository().resolve("HEAD");
            String commit = head == null ? "unknown" : head.getName();
            log.info("Clone done, HEAD={}", commit);
            return new CloneResult(target, commit, useBranch);
        }
    }

    /** Delete the clone once the snapshot is built; remediation re-clones later. */
    public void cleanup(Path dir) {
        try {
            FileSystemUtils.deleteRecursively(dir);
        } catch (IOException e) {
            log.warn("Could not delete {}: {}", dir, e.getMessage());
        }
    }
}
