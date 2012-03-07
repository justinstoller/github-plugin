package com.cloudbees.jenkins;

import com.cloudbees.jenkins.GitHubPushTrigger.DescriptorImpl;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.RootAction;
import hudson.model.UnprotectedRootAction;
import hudson.security.ACL;
import hudson.util.AdaptedIterator;
import hudson.util.Iterators.FilterIterator;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.kohsuke.github.GitHub;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.logging.Level.*;

/**
 * Receives github hook.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class GitHubWebHook implements UnprotectedRootAction {
    /**
     * Would something like this be a better matcher?:
     * "((https?|git)://|git@)([^/]+)[/:]([^/]+)/([^/]+)(.git)?"
     *   -- Justin
     */
    private static final Pattern REPOSITORY_NAME_PATTERN = Pattern.compile("https?://([^/]+)/([^/]+)/([^/]+)");

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return "github-webhook";
    }

    /**
     * Logs in as the given user and returns the connection object.
     *
     * I have no idea when or where this is being called  --Justin
     */
    public Iterable<GitHub> login(String host, String userName) {
        if (host.equals("github.com")) {
            final List<Credential> l = DescriptorImpl.get().getCredentials();

            // if the username is not an organization, we should have the right user account on file
            for (Credential c : l) {
                if (c.username.equals(userName))
                    try {
                        return Collections.singleton(c.login());
                    } catch (IOException e) {
                        LOGGER.log(WARNING,"Failed to login with username="+c.username,e);
                        return Collections.emptyList();
                    }
            }

            // otherwise try all the credentials since we don't know which one would work
            return new Iterable<GitHub>() {
                public Iterator<GitHub> iterator() {
                    return new FilterIterator<GitHub>(
                        new AdaptedIterator<Credential,GitHub>(l) {
                            protected GitHub adapt(Credential c) {
                                try {
                                    return c.login();
                                } catch (IOException e) {
                                    LOGGER.log(WARNING,"Failed to login with username="+c.username,e);
                                    return null;
                                }
                            }
                    }) {
                        protected boolean filter(GitHub g) {
                            return g!=null;
                        }
                    };
                }
            };
        } else {
            return Collections.<GitHub> emptyList();
        }
    }

    /*

    A Standard Push Request:
    {
        "after":"ea50ac0026d6d9c284e04afba1cc95d86dc3d976",
        "before":"501f46e557f8fc5e0fa4c88a7f4597ef597dd1bf",
        "commits":[
            {
                "added":["b"],
                "author":{"email":"kk@kohsuke.org","name":"Kohsuke Kawaguchi","username":"kohsuke"},
                "id":"3c696af1225e63ed531f5656e8f9cc252e4c96a2",
                "message":"another commit",
                "modified":[],
                "removed":[],
                "timestamp":"2010-12-08T14:31:24-08:00",
                "url":"https://github.com/kohsuke/foo/commit/3c696af1225e63ed531f5656e8f9cc252e4c96a2"
            },{
                "added":["d"],
                "author":{"email":"kk@kohsuke.org","name":"Kohsuke Kawaguchi","username":"kohsuke"},
                "id":"ea50ac0026d6d9c284e04afba1cc95d86dc3d976",
                "message":"new commit",
                "modified":[],
                "removed":[],
                "timestamp":"2010-12-08T14:32:11-08:00",
                "url":"https://github.com/kohsuke/foo/commit/ea50ac0026d6d9c284e04afba1cc95d86dc3d976"
            }
        ],
        "compare":"https://github.com/kohsuke/foo/compare/501f46e...ea50ac0",
        "forced":false,
        "pusher":{"email":"kk@kohsuke.org","name":"kohsuke"},
        "ref":"refs/heads/master",
        "repository":{
            "created_at":"2010/12/08 12:44:13 -0800",
            "description":"testing",
            "fork":false,
            "forks":1,
            "has_downloads":true,
            "has_issues":true,
            "has_wiki":true,
            "homepage":"testing",
            "name":"foo",
            "open_issues":0,
            "owner":{"email":"kk@kohsuke.org","name":"kohsuke"},
            "private":false,
            "pushed_at":"2010/12/08 14:32:23 -0800",
            "url":"https://github.com/kohsuke/foo","watchers":1
        }
    }

    A Standard Pull Request: -- Justin
    {
        "number": 1,
        "repository": {
            "name": "puppet-rvm",
            "created_at": "2012-01-18T06:58:50Z",
            "updated_at": "2012-03-05T23:56:03Z",
            "url": "https://api.github.com/repos/justinstoller/puppet-rvm",
            "id": 3206659,
            "pushed_at": "2012-03-05T23:56:01Z",
            "owner": {
                "avatar_url": "https://secure.gravatar.com/avatar/ef7d527c37bdd4b110e591ca09b177e0?d=https://a248.e.akamai.net/assets.github.com%2Fimages%2Fgravatars%2Fgravatar-140.png",
                "gravatar_id": "ef7d527c37bdd4b110e591ca09b177e0",
                "url": "https://api.github.com/users/justinstoller",
                "id": 241423,
                "login": "justinstoller"
            }
        },
        "pull_request": {
            "issue_url": "https://github.com/justinstoller/puppet-rvm/issues/1",
            "head": {
                "repo": {
                    "master_branch": null,
                    "name": "puppet-rvm",
                    "created_at": "2012-01-18T06:58:50Z",
                    "size": 140,
                    "has_wiki": true,
                    "clone_url": "https://github.com/justinstoller/puppet-rvm.git",
                    "updated_at": "2012-03-05T23:56:03Z",
                    "watchers": 1,
                    "private": false,
                    "git_url": "git://github.com/justinstoller/puppet-rvm.git",
                    "url": "https://api.github.com/repos/justinstoller/puppet-rvm",
                    "ssh_url": "git@github.com:justinstoller/puppet-rvm.git",
                    "fork": false,
                    "language": "Puppet",
                    "pushed_at": "2012-03-05T23:56:01Z",
                    "svn_url": "https://github.com/justinstoller/puppet-rvm",
                    "id": 3206659,
                    "open_issues": 1,
                    "mirror_url": null,
                    "has_downloads": true,
                    "has_issues": true,
                    "homepage": "",
                    "description": "",
                    "forks": 1,
                    "html_url": "https://github.com/justinstoller/puppet-rvm",
                    "owner": {
                        "avatar_url": "https://secure.gravatar.com/avatar/ef7d527c37bdd4b110e591ca09b177e0?d=https://a248.e.akamai.net/assets.github.com%2Fimages%2Fgravatars%2Fgravatar-140.png",
                        "gravatar_id": "ef7d527c37bdd4b110e591ca09b177e0",
                        "url": "https://api.github.com/users/justinstoller",
                        "id": 241423,
                        "login": "justinstoller"
                      }
                },
              "label": "justinstoller:test_branch",
              "sha": "a805aa72284b4f76e2dd026c11709c9161c343d6",
              "ref": "test_branch",
              "user": {
                  "avatar_url": "https://secure.gravatar.com/avatar/ef7d527c37bdd4b110e591ca09b177e0?d=https://a248.e.akamai.net/assets.github.com%2Fimages%2Fgravatars%2Fgravatar-140.png",
                  "gravatar_id": "ef7d527c37bdd4b110e591ca09b177e0",
                  "url": "https://api.github.com/users/justinstoller",
                  "id": 241423,
                  "login": "justinstoller"
                }
            },
            "number": 1,
            "merged": false,
            "changed_files": 1,
            "created_at": "2012-03-05T23:56:50Z",
            "merged_by": null,
            "body": "this is the body",
            "comments": 0,
            "title": "test",
            "additions": 0,
            "updated_at": "2012-03-05T23:56:50Z",
            "diff_url": "https://github.com/justinstoller/puppet-rvm/pull/1.diff",
            "_links": {
                "html": {
                    "href": "https://github.com/justinstoller/puppet-rvm/pull/1"
                },
                "self": {
                    "href": "https://api.github.com/repos/justinstoller/puppet-rvm/pulls/1"
                },
                "comments": {
                    "href": "https://api.github.com/repos/justinstoller/puppet-rvm/issues/1/comments"
                },
                "review_comments": {
                    "href": "https://api.github.com/repos/justinstoller/puppet-rvm/pulls/1/comments"
                }
            },
            "url": "https://api.github.com/repos/justinstoller/puppet-rvm/pulls/1",
            "id": 932451,
            "patch_url": "https://github.com/justinstoller/puppet-rvm/pull/1.patch",
            "mergeable": null,
            "closed_at": null,
            "merged_at": null,
            "commits": 1,
            "deletions": 0,
            "review_comments": 0,
            "html_url": "https://github.com/justinstoller/puppet-rvm/pull/1",
            "user": {
                "avatar_url": "https://secure.gravatar.com/avatar/ef7d527c37bdd4b110e591ca09b177e0?d=https://a248.e.akamai.net/assets.github.com%2Fimages%2Fgravatars%2Fgravatar-140.png",
                "gravatar_id": "ef7d527c37bdd4b110e591ca09b177e0",
                "url": "https://api.github.com/users/justinstoller",
                "id": 241423,
                "login": "justinstoller"
            },
            "state": "open",
            "base": {
                "repo": {
                    "master_branch": null,
                    "name": "puppet-rvm",
                    "created_at": "2012-01-18T06:58:50Z",
                    "size": 140,
                    "has_wiki": true,
                    "clone_url": "https://github.com/justinstoller/puppet-rvm.git",
                    "updated_at": "2012-03-05T23:56:03Z",
                    "watchers": 1,
                    "private": false,
                    "git_url": "git://github.com/justinstoller/puppet-rvm.git",
                    "url": "https://api.github.com/repos/justinstoller/puppet-rvm",
                    "ssh_url": "git@github.com:justinstoller/puppet-rvm.git",
                    "fork": false,
                    "language": "Puppet",
                    "pushed_at": "2012-03-05T23:56:01Z",
                    "svn_url": "https://github.com/justinstoller/puppet-rvm",
                    "id": 3206659,
                    "open_issues": 1,
                    "mirror_url": null,
                    "has_downloads": true,
                    "has_issues": true,
                    "homepage": "",
                    "description": "",
                    "forks": 1,
                    "html_url": "https://github.com/justinstoller/puppet-rvm",
                    "owner": {
                        "avatar_url": "https://secure.gravatar.com/avatar/ef7d527c37bdd4b110e591ca09b177e0?d=https://a248.e.akamai.net/assets.github.com%2Fimages%2Fgravatars%2Fgravatar-140.png",
                        "gravatar_id": "ef7d527c37bdd4b110e591ca09b177e0",
                        "url": "https://api.github.com/users/justinstoller",
                        "id": 241423,
                        "login": "justinstoller"
                    }
                },
                "label": "justinstoller:master",
                "sha": "315e45a86055594ac6b1d477ed284f88e96b3f81",
                "ref": "master",
                "user": {
                    "avatar_url": "https://secure.gravatar.com/avatar/ef7d527c37bdd4b110e591ca09b177e0?d=https://a248.e.akamai.net/assets.github.com%2Fimages%2Fgravatars%2Fgravatar-140.png",
                    "gravatar_id": "ef7d527c37bdd4b110e591ca09b177e0",
                    "url": "https://api.github.com/users/justinstoller",
                    "id": 241423,
                    "login": "justinstoller"
                }
            }
        },
        "sender": {
            "avatar_url": "https://secure.gravatar.com/avatar/ef7d527c37bdd4b110e591ca09b177e0?d=https://a248.e.akamai.net/assets.github.com%2Fimages%2Fgravatars%2Fgravatar-140.png",
            "gravatar_id": "ef7d527c37bdd4b110e591ca09b177e0",
            "url": "https://api.github.com/users/justinstoller",
            "id": 241423,
            "login": "justinstoller"
        },
        "action": "opened"
     }

     */


    /**
     * 1 push to 2 branches will result in 2 pushes.
     */
    public void doIndex(StaplerRequest req) {
      /**
       * This should be a class of its own, until then I wrapped the
       * original in the else block  -- Justin
       *
       * Could this be a GitHubResponseParser? Something that will parse
       * the response and return only the interesting bits. We could then
       * instatiate a GitHubRepository that contained the correct
       * information for each... It could also have a member
       * <GitHubRepositoryName> name??
       */
        JSONObject o = JSONObject.fromObject(req.getParameter("payload"));
        if o.has("pull_request") {
            JSONObject pullRequest = o.getJSONObject("pull_request");
            /**
             * Get the base and head information for the pull request
             * (the base info must match a project in Jenkins)
             *   -- Justin
             */
            JSONObject head        = pullRequest.getJSONObject("head");
            JSONObject headRepo    = head.getJSONObject("repo");

            String headHttpUrl     = headRepo.getString("url");
            String headGitUrl      = headRepo.getString("git_url");
            String headSshUrl      = headRepo.getString("ssh_url");
            String headLabel       = head.getString("label");
            String headSHA         = head.getString("sha");

            JSONObject base        = pullRequest.getJSONObject("base");
            JSONObject baseRepo    = base.getJSONObject("repo");

            String baseHttpUrl     = baseRepo.getString("url");
            String baseGitUrl      = baseRepo.getString("git_url");
            String baseSshUrl      = baseRepo.getString("ssh_url");
            String baseLabel       = base.getString("label");
            String baseSHA         = base.getString("sha");
        } else {
            JSONObject repository = o.getJSONObject("repository");
            String repoUrl = repository.getString("url"); // something like 'https://github.com/kohsuke/foo'
            String repoName = repository.getString("name"); // 'foo' portion of the above URL
            String ownerName = repository.getJSONObject("owner").getString("name"); // 'kohsuke' portion of the above URL
        }




        LOGGER.info("Received POST for "+repoUrl);
        LOGGER.fine("Full details of the POST was "+o.toString());
        /**
         * Shouldn't we use a more complete Regex and check against an
         * array?/list? of [ 'HttpUrl', 'GitUrl', 'SshUrl' ]??
         * I think this won't allow for private repos specified with
         * 'git@' or 'git://'....
         *
         * How would GitHubRepositoryName class (and the passing of
         * matcher.group(1) below have to change to make that possible?
         *
         * I think this is possible. GitHubRepositoryName.create(String url)
         * will parse the url against all different possible git urls.
         * Using the create method might be better than new with the
         * host, user, and repo already parsed from only an 'https' url.
         *   -- Justin
         */
        Matcher matcher = REPOSITORY_NAME_PATTERN.matcher(repoUrl);
        if (matcher.matches()) {
            /**
             * run in high privilege to see all the projects anonymous
             * users don't see. This is safe because when we actually
             * schedule a build, it's a build that can happen at some
             * random time anyway.
             */
            Authentication old = SecurityContextHolder.getContext().getAuthentication();
            SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
            try {
                GitHubRepositoryName changedRepository = new GitHubRepositoryName(matcher.group(1), ownerName, repoName);
                // For all the jobs Hudson (Jenkins) knows about...
                for (AbstractProject<?,?> job : Hudson.getInstance().getAllItems(AbstractProject.class)) {
                    // Get any that have a GitHubPushTrigger configured...
                    GitHubPushTrigger trigger = job.getTrigger(GitHubPushTrigger.class);
                    if (trigger!=null) {
                        LOGGER.fine("Considering to poke "+job.getFullDisplayName());
                        // And finally...
                        if (trigger.getGitHubRepositories().contains(changedRepository))
                            /**
                             * Schedule a build
                             * How can we get onPost (which just calls
                             * Queue.execute(this) to clone the base repo,
                             * checkout the correct base branch/commit,
                             * fetch the head branch/commit, merge them
                             * and then start the build steps?
                             * See GitHubPushTrigger.onPost for more
                             *   -- Justin
                             */
                            trigger.onPost();
                        else
                            LOGGER.fine("Skipped "+job.getFullDisplayName()+" because it doesn't have a matching repository.");
                    }
                }
            } finally {
                SecurityContextHolder.getContext().setAuthentication(old);
            }
        } else {
            LOGGER.warning("Malformed repo url "+repoUrl);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(GitHubWebHook.class.getName());

    public static GitHubWebHook get() {
        return Hudson.getInstance().getExtensionList(RootAction.class).get(GitHubWebHook.class);
    }

    static {
        // hide "Bad input type: "search", creating a text input" from createElementNS
        Logger.getLogger(com.gargoylesoftware.htmlunit.html.InputElementFactory.class.getName()).setLevel(WARNING);
    }
}
