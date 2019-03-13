class AppDirectory {
    static HashMap<String,HashMap> appMap = new LinkedHashMap<String,CommonAppJobs>()

    static registerApp(appName, jobs)
    {
        appMap.put(appName, jobs)
    }

    static build(dslFactory) {
        dslFactory.nestedView("App Directory") {
            views {
                def apps = appMap.keySet().toArray()
                for (int appIndex = 0; appIndex < apps.size(); appIndex++) {
                    def app = apps[appIndex]
                    def jobs = appMap.get(app)
                    deliveryPipelineView("${app}") {
                        allowPipelineStart(true)
                        allowRebuild(true)
                        columns(1)
                        enableManualTriggers(true)
                        pipelineInstances(1)
                        pipelines {
                            component("${app} Build And Deploy Pipline", "${jobs.getBuildAndTestPRJobName()}")
                            component("Restart STG", "${jobs.getrestartSTGJobName()}")
                            component("Restart PRD", "${jobs.getrestartPRDJobName()}")
                        }
                        showAggregatedPipeline(false)
                        showAvatars(false)
                        showChangeLog(false)
                        showDescription(false)
                        showPromotions(false)
                        showTotalBuildTime(false)
                        updateInterval(2)
                    }
                }
            }
        }
    }
}
