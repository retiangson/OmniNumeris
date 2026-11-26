package com.omninumeris.deployment

import android.content.Context
import com.omninumeris.deployment.common.DeploymentConstants
import com.omninumeris.monitoring.MonitoringSystem

/**
 * Centralized Deployment Manager
 * Coordinates deployment automation, monitoring, and status tracking.
 */
class DeploymentManager(private val context: Context) {
    private val deploymentAutomation = DeploymentAutomation(context)
    private val monitoringSystem = MonitoringSystem(context)
    private val statusTracker = DeploymentStatusTracker(context)

    /**
     * Initialize all deployment and monitoring components
     */
    fun initialize() {
        deploymentAutomation.initialize()
        monitoringSystem.initialize()
        statusTracker.initialize()

        // Log initialization
        monitoringSystem.logInfo("DeploymentManager initialized successfully")
    }

    /**
     * Start automated deployment process
     */
    fun startDeployment() {
        monitoringSystem.logInfo("Starting automated deployment process")
        deploymentAutomation.startDeployment()
    }

    /**
     * Get current deployment status
     */
    fun getCurrentStatus(): DeploymentStatus {
        return deploymentAutomation.getCurrentStatus()
    }

    /**
     * Check if deployment is needed
     */
    fun isDeploymentNeeded(): Boolean {
        return deploymentAutomation.isDeploymentNeeded()
    }

    /**
     * Get deployment configuration
     */
    fun getDeploymentConfig(): DeploymentConfig {
        return deploymentAutomation.getDeploymentConfig()
    }

    /**
     * Get monitoring system instance
     */
    fun getMonitoringSystem(): MonitoringSystem {
        return monitoringSystem
    }

    /**
     * Get status tracker instance
     */
    fun getStatusTracker(): DeploymentStatusTracker {
        return statusTracker
    }

    /**
     * Collect and log performance metrics
     */
    fun collectPerformanceMetrics() {
        val metrics = monitoringSystem.collectPerformanceMetrics()
        monitoringSystem.logInfo("Performance Metrics - CPU: ${metrics.cpuUsage}%, Memory: ${metrics.memoryUsage}%")
    }

    /**
     * Generate deployment report
     */
    fun generateDeploymentReport(): DeploymentReport {
        return statusTracker.generateDeploymentReport()
    }

    /**
     * Get deployment statistics
     */
    fun getDeploymentStatistics(): DeploymentStatistics {
        return statusTracker.getDeploymentStatistics()
    }

    /**
     * Send monitoring data to remote server
     */
    fun sendMonitoringData() {
        monitoringSystem.sendMonitoringData()
    }
}