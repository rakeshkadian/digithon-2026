package com.copilot.git;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Commits generated changes to a new branch, pushes it and opens a GitHub pull request. */
@Slf4j
@Service
public class GitPublishService {

    private final String authorName;
    private final String authorEmail;
    private final RestClient github = RestClient.builder().baseUrl("https://api.github.com").build();

    public GitPublishService(@Value("${copilot.git.author-name}") String authorName,
                             @Value("${copilot.git.author-email}") String authorEmail) {
        this.authorName = authorName;
        this.authorEmail = authorEmail;
    }

    public record PublishResult(String branch, String commitId, String prUrl) {}

    public PublishResult publish(Path repoDir, String repoUrl, String baseBranch, String token,
                                 String newBranch, String commitMessage, String prTitle, String prBody) throws Exception {
        try (Git git = Git.open(repoDir.toFile())) {
            git.checkout().setCreateBranch(true).setName(newBranch).call();
            git.add().addFilepattern(".").call();
            var commit = git.commit().setMessage(commitMessage).setAuthor(authorName, authorEmail).call();

            git.push()
               .setRemote("origin")
               .setRefSpecs(new org.eclipse.jgit.transport.RefSpec(newBranch + ":" + newBranch))
               .setCredentialsProvider(new UsernamePasswordCredentialsProvider("x-access-token", token))
               .call();
            log.info("Pushed branch {} ({})", newBranch, commit.getName());

            String prUrl = openPullRequest(repoUrl, token, newBranch, baseBranch, prTitle, prBody);
            return new PublishResult(newBranch, commit.getName(), prUrl);
        }
    }

    private String openPullRequest(String repoUrl, String token, String head, String base, String title, String body) {
        Matcher m = Pattern.compile("github\\.com[:/]([^/]+)/([^/.]+)").matcher(repoUrl);
        if (!m.find()) {
            log.info("Not a GitHub URL, skipping PR creation");
            return null;
        }
        String owner = m.group(1), repo = m.group(2);
        try {
            Map<?, ?> resp = github.post()
                    .uri("/repos/{owner}/{repo}/pulls", owner, repo)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("title", title, "head", head, "base", base, "body", body))
                    .retrieve()
                    .body(Map.class);
            return resp == null ? null : String.valueOf(resp.get("html_url"));
        } catch (Exception e) {
            log.warn("PR creation failed: {}", e.getMessage());
            return null;
        }
    }
}
