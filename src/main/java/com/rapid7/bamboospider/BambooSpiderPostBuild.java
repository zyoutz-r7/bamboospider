package com.rapid7.bamboospider;

import com.atlassian.bamboo.build.CustomBuildProcessorServer;
import com.atlassian.bamboo.builder.BuildState;
import com.atlassian.bamboo.task.TaskDefinition;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.extras.common.log.Logger;
import org.jetbrains.annotations.NotNull;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;
import com.rapid7.appspider.*;

/**
 * Created by nbugash on 08/07/15.
 */
public class BambooSpiderPostBuild implements CustomBuildProcessorServer {

    private static final Logger.Log log = Logger.getInstance(BambooSpiderPostBuild.class);
    private BuildContext buildContext;
    private TaskDefinition taskDefinition;
    private String restUrl = null;
    private String login = null;
    private String password = null;
    private String authToken = null;
    private String scanConfig = null;
    private Boolean scan = false;

    private final String COMPLETE_SCAN = "Completed|Stopped|ReportError";
    private final Integer SCAN_CHECK_INTERVAL = 60;


    @Override
    public void init(BuildContext buildContext) {
        log.info("Initializing BambooSpiderPostBuild!!");
        this.buildContext = buildContext;
        this.taskDefinition = buildContext.getBuildDefinition().getTaskDefinitions().get(0);
    }

    @NotNull
    @Override
    public BuildContext call() throws InterruptedException, Exception {
        log.info("Running BambooSpiderPostBuild");
        Map<String,String> customConfig = this.buildContext.getBuildDefinition().getCustomConfiguration();
        if (customConfig == null){
            log.error("Post command not set to run on the server. Skipping..");
            return buildContext;
        }

        log.info("Post command is set to run on the server");
        this.restUrl = getRestUrl(this.taskDefinition);
        this.login = getLogin(this.taskDefinition);
        this.password = getPassword(this.taskDefinition);
        this.scanConfig = getScanConfig(this.taskDefinition);
        this.scan = getScan(this.taskDefinition);
        runScan();
        return this.buildContext;
    }

    public void runScan(){
        if (scan){
            log.info("Generating Auth Token");
            authToken = Authentication.authenticate(this.restUrl, this.login, this.password);

            if (authToken == null || authToken.isEmpty()) {
                log.error("Invalid AppSpider authentication token: " + authToken);
                buildContext.getBuildResult().setBuildState(BuildState.FAILED);
            } else {
                log.info("Starting scan for " + this.scanConfig + " scan config");
                JSONObject scanResult = ScanManagement.runScanByConfigName(this.restUrl, authToken, this.scanConfig);
                log.info("Scan Request Result: " + scanResult);

                if (scanResult == null){
                    log.error("Error while attempting to start scan, check ASE server logs");
                }

                String scanId = scanResult.getJSONObject("Scan").getString("Id");
                String scan_status = ScanManagement.getScanStatus(this.restUrl, authToken, scanId);

                while(!scan_status.matches(COMPLETE_SCAN)) {
                    log.info("Waiting for scan to finish");
                    try {
                        TimeUnit.SECONDS.sleep(SCAN_CHECK_INTERVAL);
                        // Auth again due to session timeout - look at better way to do this
                        authToken = Authentication.authenticate(this.restUrl, this.login, this.password);
                        scan_status = ScanManagement.getScanStatus(this.restUrl, authToken, scanId);
                        log.info("Scan status: [" + scan_status +"]");
                    } catch (InterruptedException e) {
                        log.error("Failure while running scan config: " + e);
                    }
                }
            }

            log.info("Finished running scan.");
        }else{
            log.info("We're not going to scan "+this.scanConfig);
        }
    }
    private String getRestUrl(TaskDefinition taskDefinition){
        log.info("Getting the AppSpider Rest Url");
        return taskDefinition.getConfiguration().get("restUrl");
    }

    private String getLogin(TaskDefinition taskDefinition){
        log.info("Getting the login account for " + restUrl );
        return taskDefinition.getConfiguration().get("login");
    }
    private String getPassword(TaskDefinition taskDefinition){
        log.info("Getting the password");
        return taskDefinition.getConfiguration().get("password");
    }
    private String getScanConfig(TaskDefinition taskDefinition){
        log.info("Getting the config scan");
        return taskDefinition.getConfiguration().get("scanConfig");
    }
    private Boolean getScan(TaskDefinition taskDefinition){
        log.info("Checking if we are going to scan...");
        if(taskDefinition.getConfiguration().get("scan") == null) {
            log.info("Scanning is not enabled");
            return false;
        }else{
            log.info("Scanning is enabled");
            return true;
        }
    }
}
