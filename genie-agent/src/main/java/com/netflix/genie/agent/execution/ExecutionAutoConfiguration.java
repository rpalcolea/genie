/*
 *
 *  Copyright 2018 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.netflix.genie.agent.execution;

import com.netflix.genie.agent.AgentMetadata;
import com.netflix.genie.agent.cli.ArgumentDelegates;
import com.netflix.genie.agent.cli.JobRequestConverter;
import com.netflix.genie.agent.execution.process.JobProcessManager;
import com.netflix.genie.agent.execution.services.AgentFileStreamService;
import com.netflix.genie.agent.execution.services.AgentHeartBeatService;
import com.netflix.genie.agent.execution.services.AgentJobKillService;
import com.netflix.genie.agent.execution.services.AgentJobService;
import com.netflix.genie.agent.execution.services.JobSetupService;
import com.netflix.genie.agent.execution.statemachine.ExecutionContext;
import com.netflix.genie.agent.execution.statemachine.ExecutionStage;
import com.netflix.genie.agent.execution.statemachine.JobExecutionStateMachine;
import com.netflix.genie.agent.execution.statemachine.JobExecutionStateMachineImpl;
import com.netflix.genie.agent.execution.statemachine.States;
import com.netflix.genie.agent.execution.statemachine.listeners.JobExecutionListener;
import com.netflix.genie.agent.execution.statemachine.listeners.LoggingListener;
import com.netflix.genie.agent.execution.statemachine.listeners.UserConsoleLoggingListener;
import com.netflix.genie.agent.execution.statemachine.stages.ArchiveJobOutputsStage;
import com.netflix.genie.agent.execution.statemachine.stages.ClaimJobStage;
import com.netflix.genie.agent.execution.statemachine.stages.CleanupJobDirectoryStage;
import com.netflix.genie.agent.execution.statemachine.stages.ConfigureExecutionStage;
import com.netflix.genie.agent.execution.statemachine.stages.CreateJobDirectoryStage;
import com.netflix.genie.agent.execution.statemachine.stages.CreateJobScriptStage;
import com.netflix.genie.agent.execution.statemachine.stages.DetermineJobFinalStatusStage;
import com.netflix.genie.agent.execution.statemachine.stages.DownloadDependenciesStage;
import com.netflix.genie.agent.execution.statemachine.stages.HandshakeStage;
import com.netflix.genie.agent.execution.statemachine.stages.InitializeAgentStage;
import com.netflix.genie.agent.execution.statemachine.stages.LaunchJobStage;
import com.netflix.genie.agent.execution.statemachine.stages.LogExecutionErrorsStage;
import com.netflix.genie.agent.execution.statemachine.stages.ObtainJobSpecificationStage;
import com.netflix.genie.agent.execution.statemachine.stages.RefreshManifestStage;
import com.netflix.genie.agent.execution.statemachine.stages.RelocateLogFileStage;
import com.netflix.genie.agent.execution.statemachine.stages.ReserveJobIdStage;
import com.netflix.genie.agent.execution.statemachine.stages.SetJobStatusFinal;
import com.netflix.genie.agent.execution.statemachine.stages.SetJobStatusInit;
import com.netflix.genie.agent.execution.statemachine.stages.SetJobStatusRunning;
import com.netflix.genie.agent.execution.statemachine.stages.ShutdownStage;
import com.netflix.genie.agent.execution.statemachine.stages.StartFileServiceStage;
import com.netflix.genie.agent.execution.statemachine.stages.StartHeartbeatServiceStage;
import com.netflix.genie.agent.execution.statemachine.stages.StartKillServiceStage;
import com.netflix.genie.agent.execution.statemachine.stages.StopFileServiceStage;
import com.netflix.genie.agent.execution.statemachine.stages.StopHeartbeatServiceStage;
import com.netflix.genie.agent.execution.statemachine.stages.StopKillServiceStage;
import com.netflix.genie.agent.execution.statemachine.stages.WaitJobCompletionStage;
import com.netflix.genie.common.internal.services.JobArchiveService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;

import javax.validation.constraints.NotEmpty;
import java.util.Collection;
import java.util.List;

/**
 * Spring auto configuration for beans required for job execution.
 *
 * @author tgianos
 * @since 4.0.0
 */
@Configuration
public class ExecutionAutoConfiguration {

    /**
     * Provide a lazy {@link LoggingListener} bean.
     *
     * @return A {@link LoggingListener} instance
     */
    @Bean
    @Lazy
    @ConditionalOnMissingBean(LoggingListener.class)
    public LoggingListener loggingListener() {
        return new LoggingListener();
    }

    /**
     * Provide a lazy {@link UserConsoleLoggingListener} bean.
     *
     * @return A {@link UserConsoleLoggingListener} instance
     */
    @Bean
    @Lazy
    @ConditionalOnMissingBean(UserConsoleLoggingListener.class)
    public UserConsoleLoggingListener userConsoleLoggingListener() {
        return new UserConsoleLoggingListener();
    }

    /**
     * Provide the {@link ExecutionContext} bean.
     *
     * @return A {@link ExecutionContext}.
     */
    @Bean
    @Lazy
    @ConditionalOnMissingBean
    ExecutionContext executionContext(
    ) {
        return new ExecutionContext();
    }

    @Bean
    @Lazy
    JobExecutionStateMachine jobExecutionStateMachine2(
        @NotEmpty final List<ExecutionStage> executionStages,
        final ExecutionContext executionContext,
        final Collection<JobExecutionListener> listeners
    ) {
        return new JobExecutionStateMachineImpl(executionStages, executionContext, listeners);
    }

    /**
     * Create a {@link InitializeAgentStage} bean if one is not already defined.
     *
     * @param agentMetadata the agent metadata
     */
    @Bean
    @Lazy
    @Order(10)
    @ConditionalOnMissingBean(InitializeAgentStage.class)
    InitializeAgentStage initializeAgentStage(final AgentMetadata agentMetadata) {
        return new InitializeAgentStage(agentMetadata);
    }

    /**
     * Create a {@link HandshakeStage} bean if one is not already defined.
     *
     * @param agentJobService the agent job service
     */
    @Bean
    @Lazy
    @Order(20)
    @ConditionalOnMissingBean(HandshakeStage.class)
    HandshakeStage handshakeStage(final AgentJobService agentJobService) {
        return new HandshakeStage(agentJobService);
    }

    /**
     * Create a {@link ConfigureExecutionStage} bean if one is not already defined.
     *
     * @param jobRequestConverter           the job request converter
     * @param jobRequestArguments           the job request arguments group
     * @param runtimeConfigurationArguments the runtime configuration arguments group
     * @param cleanupArguments              the cleanup arguments group
     */
    @Bean
    @Lazy
    @Order(30)
    @ConditionalOnMissingBean(ConfigureExecutionStage.class)
    ConfigureExecutionStage configureExecutionStage(
        final JobRequestConverter jobRequestConverter,
        final ArgumentDelegates.JobRequestArguments jobRequestArguments,
        final ArgumentDelegates.RuntimeConfigurationArguments runtimeConfigurationArguments,
        final ArgumentDelegates.CleanupArguments cleanupArguments
    ) {
        return new ConfigureExecutionStage(
            jobRequestConverter,
            jobRequestArguments,
            runtimeConfigurationArguments,
            cleanupArguments
        );
    }

    /**
     * Create a {@link ReserveJobIdStage} bean if one is not already defined.
     *
     * @param agentJobService the agent job service
     */
    @Bean
    @Lazy
    @Order(40)
    @ConditionalOnMissingBean(ReserveJobIdStage.class)
    ReserveJobIdStage reserveJobIdStage(final AgentJobService agentJobService) {
        return new ReserveJobIdStage(agentJobService);
    }

    /**
     * Create a {@link ObtainJobSpecificationStage} bean if one is not already defined.
     *
     * @param agentJobService the agent job service
     */
    @Bean
    @Lazy
    @Order(50)
    @ConditionalOnMissingBean(ObtainJobSpecificationStage.class)
    ObtainJobSpecificationStage obtainJobSpecificationStage(final AgentJobService agentJobService) {
        return new ObtainJobSpecificationStage(agentJobService);
    }

    /**
     * Create a {@link CreateJobDirectoryStage} bean if one is not already defined.
     *
     * @param jobSetupService the job setup service
     */
    @Bean
    @Lazy
    @Order(60)
    @ConditionalOnMissingBean(CreateJobDirectoryStage.class)
    CreateJobDirectoryStage createJobDirectoryStage(final JobSetupService jobSetupService) {
        return new CreateJobDirectoryStage(jobSetupService);
    }

    /**
     * Create a {@link RelocateLogFileStage} bean if one is not already defined.
     */
    @Bean
    @Lazy
    @Order(70)
    @ConditionalOnMissingBean(RelocateLogFileStage.class)
    RelocateLogFileStage relocateLogFileStage() {
        return new RelocateLogFileStage();
    }

    /**
     * Create a {@link ClaimJobStage} bean if one is not already defined.
     *
     * @param agentJobService the agent job service
     */
    @Bean
    @Lazy
    @Order(80)
    @ConditionalOnMissingBean(ClaimJobStage.class)
    ClaimJobStage claimJobStage(final AgentJobService agentJobService) {
        return new ClaimJobStage(agentJobService);
    }

    /**
     * Create a {@link StartHeartbeatServiceStage} bean if one is not already defined.
     *
     * @param heartbeatService the heartbeat service
     */
    @Bean
    @Lazy
    @Order(90)
    @ConditionalOnMissingBean(StartHeartbeatServiceStage.class)
    StartHeartbeatServiceStage startHeartbeatServiceStage(final AgentHeartBeatService heartbeatService) {
        return new StartHeartbeatServiceStage(heartbeatService);
    }

    /**
     * Create a {@link StartKillServiceStage} bean if one is not already defined.
     *
     * @param killService the kill service
     */
    @Bean
    @Lazy
    @Order(100)
    @ConditionalOnMissingBean(StartKillServiceStage.class)
    StartKillServiceStage startKillServiceStage(final AgentJobKillService killService) {
        return new StartKillServiceStage(killService);
    }

    /**
     * Create a {@link StartFileServiceStage} bean if one is not already defined.
     *
     * @param agentFileStreamService the agent file stream service
     */
    @Bean
    @Lazy
    @Order(110)
    @ConditionalOnMissingBean(StartFileServiceStage.class)
    StartFileServiceStage startFileServiceStage(final AgentFileStreamService agentFileStreamService) {
        return new StartFileServiceStage(agentFileStreamService);
    }

    /**
     * Create a {@link SetJobStatusInit} bean if one is not already defined.
     *
     * @param agentJobService the agent job service
     */
    @Bean
    @Lazy
    @Order(120)
    @ConditionalOnMissingBean(SetJobStatusInit.class)
    SetJobStatusInit setJobStatusInit(final AgentJobService agentJobService) {
        return new SetJobStatusInit(agentJobService);
    }

    /**
     * Create a {@link CreateJobScriptStage} bean if one is not already defined.
     *
     * @param jobSetupService the job setup service
     */
    @Bean
    @Lazy
    @Order(130)
    @ConditionalOnMissingBean(CreateJobScriptStage.class)
    CreateJobScriptStage createJobScriptStage(final JobSetupService jobSetupService) {
        return new CreateJobScriptStage(jobSetupService);
    }

    /**
     * Create a {@link DownloadDependenciesStage} bean if one is not already defined.
     *
     * @param jobSetupService the job setup service
     */
    @Bean
    @Lazy
    @Order(140)
    @ConditionalOnMissingBean(DownloadDependenciesStage.class)
    DownloadDependenciesStage downloadDependenciesStage(final JobSetupService jobSetupService) {
        return new DownloadDependenciesStage(jobSetupService);
    }

    /**
     * Create a {@link RefreshManifestStage} bean if one is not already defined.
     *
     * @param agentFileStreamService the agent file stream service
     */
    @Bean
    @Lazy
    @Order(145)
    @ConditionalOnMissingBean(name = "postSetupRefreshManifestStage")
    RefreshManifestStage postSetupRefreshManifestStage(final AgentFileStreamService agentFileStreamService) {
        return new RefreshManifestStage(agentFileStreamService, States.POST_SETUP_MANIFEST_REFRESH);
    }

    /**
     * Create a {@link LaunchJobStage} bean if one is not already defined.
     *
     * @param jobProcessManager the job process manager
     */
    @Bean
    @Lazy
    @Order(150)
    @ConditionalOnMissingBean(LaunchJobStage.class)
    LaunchJobStage launchJobStage(final JobProcessManager jobProcessManager) {
        return new LaunchJobStage(jobProcessManager);
    }

    /**
     * Create a {@link RefreshManifestStage} bean if one is not already defined.
     *
     * @param agentFileStreamService the agent file stream service
     */
    @Bean
    @Lazy
    @Order(155)
    @ConditionalOnMissingBean(name = "postLaunchRefreshManifestStage")
    RefreshManifestStage postLaunchRefreshManifestStage(final AgentFileStreamService agentFileStreamService) {
        return new RefreshManifestStage(agentFileStreamService, States.POST_LAUNCH_MANIFEST_REFRESH);
    }

    /**
     * Create a {@link SetJobStatusRunning} bean if one is not already defined.
     *
     * @param agentJobService the agent job service
     */
    @Bean
    @Lazy
    @Order(160)
    @ConditionalOnMissingBean(SetJobStatusRunning.class)
    SetJobStatusRunning setJobStatusRunning(final AgentJobService agentJobService) {
        return new SetJobStatusRunning(agentJobService);
    }

    /**
     * Create a {@link WaitJobCompletionStage} bean if one is not already defined.
     *
     * @param jobProcessManager the job process manager
     */
    @Bean
    @Lazy
    @Order(170)
    @ConditionalOnMissingBean(WaitJobCompletionStage.class)
    WaitJobCompletionStage waitJobCompletionStage(final JobProcessManager jobProcessManager) {
        return new WaitJobCompletionStage(jobProcessManager);
    }

    /**
     * Create a {@link RefreshManifestStage} bean if one is not already defined.
     *
     * @param agentFileStreamService the agent file stream service
     */
    @Bean
    @Lazy
    @Order(175)
    @ConditionalOnMissingBean(name = "postExecutionRefreshManifestStage")
    RefreshManifestStage postExecutionRefreshManifestStage(final AgentFileStreamService agentFileStreamService) {
        return new RefreshManifestStage(agentFileStreamService, States.POST_EXECUTION_MANIFEST_REFRESH);
    }

    /**
     * Create a {@link DetermineJobFinalStatusStage} bean if one is not already defined.
     */
    @Bean
    @Lazy
    @Order(180)
    @ConditionalOnMissingBean(DetermineJobFinalStatusStage.class)
    DetermineJobFinalStatusStage determineJobFinalStatusStage() {
        return new DetermineJobFinalStatusStage();
    }

    /**
     * Create a {@link SetJobStatusFinal} bean if one is not already defined.
     *
     * @param agentJobService the agent job service
     */
    @Bean
    @Lazy
    @Order(190)
    @ConditionalOnMissingBean(SetJobStatusFinal.class)
    SetJobStatusFinal setJobStatusFinal(final AgentJobService agentJobService) {
        return new SetJobStatusFinal(agentJobService);
    }

    /**
     * Create a {@link StopKillServiceStage} bean if one is not already defined.
     *
     * @param killService the kill service
     */
    @Bean
    @Lazy
    @Order(200)
    @ConditionalOnMissingBean(StopKillServiceStage.class)
    StopKillServiceStage stopKillServiceStage(final AgentJobKillService killService) {
        return new StopKillServiceStage(killService);
    }

    /**
     * Create a {@link LogExecutionErrorsStage} bean if one is not already defined.
     */
    @Bean
    @Lazy
    @Order(210)
    @ConditionalOnMissingBean(LogExecutionErrorsStage.class)
    LogExecutionErrorsStage logExecutionErrorsStage() {
        return new LogExecutionErrorsStage();
    }

    /**
     * Create a {@link ArchiveJobOutputsStage} bean if one is not already defined.
     *
     * @param jobArchiveService the job archive service
     */
    @Bean
    @Lazy
    @Order(220)
    @ConditionalOnMissingBean(ArchiveJobOutputsStage.class)
    ArchiveJobOutputsStage archiveJobOutputsStage(final JobArchiveService jobArchiveService) {
        return new ArchiveJobOutputsStage(jobArchiveService);
    }

    /**
     * Create a {@link StopHeartbeatServiceStage} bean if one is not already defined.
     *
     * @param heartbeatService the heartbeat service
     */
    @Bean
    @Lazy
    @Order(230)
    @ConditionalOnMissingBean(StopHeartbeatServiceStage.class)
    StopHeartbeatServiceStage stopHeartbeatServiceStage(final AgentHeartBeatService heartbeatService) {
        return new StopHeartbeatServiceStage(heartbeatService);
    }

    /**
     * Create a {@link StopFileServiceStage} bean if one is not already defined.
     *
     * @param agentFileStreamService the agent file stream service
     */
    @Bean
    @Lazy
    @Order(240)
    @ConditionalOnMissingBean(StopFileServiceStage.class)
    StopFileServiceStage stopFileServiceStage(final AgentFileStreamService agentFileStreamService) {
        return new StopFileServiceStage(agentFileStreamService);
    }

    /**
     * Create a {@link CleanupJobDirectoryStage} bean if one is not already defined.
     *
     * @param jobSetupService the job setup service
     */
    @Bean
    @Lazy
    @Order(250)
    @ConditionalOnMissingBean(CleanupJobDirectoryStage.class)
    CleanupJobDirectoryStage cleanupJobDirectoryStage(final JobSetupService jobSetupService) {
        return new CleanupJobDirectoryStage(jobSetupService);
    }

    /**
     * Create a {@link ShutdownStage} bean if one is not already defined.
     */
    @Bean
    @Lazy
    @Order(260)
    @ConditionalOnMissingBean(ShutdownStage.class)
    ShutdownStage shutdownStage() {
        return new ShutdownStage();
    }
}
