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
        this.deployToStg(dslFactory)
        this.deployToPrd(dslFactory)
        this.restartStg(dslFactory)
        this.restartPrd(dslFactory)
        AppDirectory.registerApp(appName, this)
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
tar -zcvf archive.tar.gz --exclude=./archive.tar.gz ./*  ./.git
''')
            }

            publishers {
                archiveArtifacts("archive.tar.gz")
                wsCleanup()
                buildPipelineTrigger("${this.getDeployToSTGJobName()}, ${this.getDeployToPRDJobName()}")
            }
        }
    }

    void deployToStg(dslFactory) {
        dslFactory.freeStyleJob("${this.getDeployToSTGJobName()}") {
            throttleConcurrentBuilds {
                maxPerNode(1)
                maxTotal(1)
            }

            logRotator(-1, -1, -1, 5)

            concurrentBuild()

            triggers {
                upstream("${this.getBuildAndTestJobName()}", 'SUCCESS')
            }

            steps {
                copyArtifacts("${this.getBuildAndTestJobName()}") {
                    buildSelector {
                        latestSuccessful(true)
                    }
                }

                shell('''
gzip -dc archive.tar.gz | tar xf -
rm archive.tar.gz
linchpin deploy
''')
            }

            publishers {
                wsCleanup()
            }
        }
    }

    void deployToPrd(dslFactory) {
        dslFactory.freeStyleJob("${this.getDeployToPRDJobName()}") {
            throttleConcurrentBuilds {
                maxPerNode(1)
                maxTotal(1)
            }

            logRotator(-1, -1, -1, 5)

            concurrentBuild()

            steps {
                copyArtifacts("${this.getBuildAndTestJobName()}") {
                    buildSelector {
                        latestSuccessful(true)
                    }
                }

                shell('''
gzip -dc archive.tar.gz | tar xf -
rm archive.tar.gz
linchpin deploy
''')
            }

            publishers {
                wsCleanup()
            }
        }
    }

    void restartStg(dslFactory) {
        dslFactory.freeStyleJob("${this.getrestartSTGJobName()}") {
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

            steps {
                shell("kubernetes-restart ${this.appName} cicd-example")
            }

            publishers {
                wsCleanup()
            }
        }
    }

    void restartPrd(dslFactory) {
        dslFactory.freeStyleJob("${this.getrestartPRDJobName()}") {
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

            steps {
                shell("kubernetes-restart ${this.appName} cicd-example")
            }

            publishers {
                wsCleanup()
            }
        }
    }

    GString getBuildAndTestPRJobName() { return "${this.appName}_0_Build_and_Test_PR" }

    GString getBuildAndTestJobName() { return "${this.appName}_1_Build_and_Test" }

    GString getDeployToSTGJobName() { return "${this.appName}_3_Deploy_to_STG" }

    GString getDeployToPRDJobName() { return "${this.appName}_3_Deploy_to_PRD" }

    GString getrestartSTGJobName() { return "${this.appName}_4_Restart_STG" }

    GString getrestartPRDJobName() { return "${this.appName}_4_Restart_PRD" }
}