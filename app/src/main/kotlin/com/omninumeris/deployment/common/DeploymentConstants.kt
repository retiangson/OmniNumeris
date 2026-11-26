package com.omninumeris.deployment.common

/**
 * Common constants for deployment and monitoring systems
 */
object DeploymentConstants {
    // Shared preferences keys
    const val PREFS_NAME = "deployment_prefs"
    const val KEY_LAST_DEPLOYMENT_STATUS = "last_deployment_status"
    const val KEY_DEPLOYMENT_HISTORY = "deployment_history"

    // File paths
    const val LOG_FILE_PATH = "app_monitoring.log"
    const val DEPLOYMENT_HISTORY_FILE = "deployment_history.json"

    // Deployment environments
    const val ENVIRONMENT_PRODUCTION = "production"
    const val ENVIRONMENT_STAGING = "staging"
    const val ENVIRONMENT_DEVELOPMENT = "development"

    // Default timeouts
    const val DEFAULT_BUILD_TIMEOUT = 300000L // 5 minutes
    const val DEFAULT_DEPLOYMENT_TIMEOUT = 600000L // 10 minutes

    // Monitoring intervals
    const val PERFORMANCE_MONITORING_INTERVAL = 30000L // 30 seconds
    const val HEALTH_REPORT_INTERVAL = 300000L // 5 minutes

    // Error codes
    const val ERROR_CODE_UNKNOWN = "ERROR_UNKNOWN"
    const val ERROR_CODE_BUILD_FAILED = "ERROR_BUILD_FAILED"
    const val ERROR_CODE_DEPLOYMENT_FAILED = "ERROR_DEPLOYMENT_FAILED"
    const val ERROR_CODE_NETWORK_ERROR = "ERROR_NETWORK_ERROR"
}