class AppDirectory {
    static HashMap<String,HashMap> appMap = new LinkedHashMap<String,HashMap>()

    static registerApp(appName, jobName)
    {
        appMap.put(appName, jobName)
    }

    static build(dslFactory) {
        dslFactory.nestedView("App Directory") {
            views {
                def apps = appMap.keySet().toArray()
                for (int appIndex = 0; appIndex < apps.size(); appIndex++) {
                    def app = apps[appIndex]
                    def jobName = appMap.get(app)
                    deliveryPipelineView("${app}") {
                        allowPipelineStart(true)
                        allowRebuild(true)
                        columns(1)
                        enableManualTriggers(true)
                        pipelineInstances(1)
                        pipelines {
                            component("${app} Build And Deploy Pipline", "${jobName}")
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
