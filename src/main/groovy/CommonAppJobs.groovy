class CommonAppJobs {
    String appName = null
    String githubRepo = null
    String git_repository = "git@github.com"
    String gitCredentials = "github3"

    void initialPipelineWrapper(delegate) {
        this.commonWrappers(delegate, false)
    }

    void githubPullRequestWrapper(delegate) {
        this.commonWrappers(delegate, true)
    }

    void commonWrappers(delegate, Boolean pullRequestJob = false, String pipelineBuildId = '${GIT_REVISION, length=7}') {
        delegate.wrappers {
            preBuildCleanup {}
            ansiColorBuildWrapper {
                colorMapName('xterm')
            }

            if(pullRequestJob) {
                buildName('${GIT_BRANCH}')
            } else {
                deliveryPipelineVersion(pipelineBuildId, true)
            }
        }
    }


    void build(dslFactory) {
        this.buildAndTestPR(dslFactory)
        this.buildAndTest(dslFactory)
    }

    void buildAndTestPR(dslFactory) {
        dslFactory.job("${this.getBuildAndTestPRJobName()}") {
            scm {
                git {
                    remote {
                        github("${this.githubRepo}", 'https','github.com')
                        url("${this.git_repository}:${this.githubRepo}.git")
                        credentials(this.gitCredentials)
                        refspec('+refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*')
                    }
                    branch('${sha1}')
                }
            }

            logRotator(-1, 30, -1, -1)

            triggers {
                githubPullRequest {
                    orgWhitelist(['DataConstruct'])
                    onlyTriggerPhrase(false)
                    useGitHubHooks(false)
                    permitAll()
                    autoCloseFailedPullRequests(false)
                    displayBuildErrorsOnDownstreamBuilds(false)
                    whiteListTargetBranches(['master'])
                    allowMembersOfWhitelistedOrgsAsAdmin()
                    cron('* * * * *')
                }
            }
            githubPullRequestWrapper(delegate)

            steps {
                shell('''
linchpin build
''')
            }
            publishers {
                mergeGithubPullRequest {
                    onlyAdminsMerge(false)
                    disallowOwnCode(false)
                    failOnNonMerge()
                    deleteOnMerge(true)
                }
            }
        }
    }

    void buildAndTest(dslFactory) {
        dslFactory.freeStyleJob("${this.getBuildAndTestJobName()}") {
            properties {
                githubProjectUrl("https://github.com/${this.githubRepo}/")
            }

            logRotator(-1, 30, -1, 5)

            concurrentBuild()
            scm {
                git {
                    remote {
                        url("${this.git_repository}:${this.githubRepo}.git")
                        credentials(this.gitCredentials)
                        branch('master')
                    }
                    extensions {
                        localBranch {
                            localBranch('master')
                        }
                    }
                }
            }
            triggers {
                scm('* * * * *')
            }

            initialPipelineWrapper(delegate)

            steps {
                shell('''
linchpin build
linchpin push
linchpin deploy      
''')
            }

            publishers {
                wsCleanup()
            }
        }
    }

    GString getBuildAndTestPRJobName() { return "${this.appName}_0_Build_and_Test_PR"; }

    GString getBuildAndTestJobName() { return "${this.appName}_1_Build_and_Test"; }

    GString getDeployToPRDJobName() { return "${this.appName}_3_Deploy_to_PRD"; }
}