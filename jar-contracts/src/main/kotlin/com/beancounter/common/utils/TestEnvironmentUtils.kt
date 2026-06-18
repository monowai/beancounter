package com.beancounter.common.utils

/**
 * Utility class for detecting test environments.
 *
 * This class provides methods to determine if the current application
 * is running in a test environment, which is useful for enabling
 * test-only functionality like data purging.
 */
object TestEnvironmentUtils {
    /**
     * Detect if we're running in a test environment.
     *
     * Uses multiple detection mechanisms:
     * - Spring profiles containing "test" or "testcontainers"
     * - Java classpath containing "test", "junit", or "gradle"
     * - Working directory containing "build"
     *
     * @return true if running in a test environment, false otherwise
     */
    fun isTestEnvironment(): Boolean {
        val activeProfiles = System.getProperty("spring.profiles.active") ?: ""
        val classpath = System.getProperty("java.class.path") ?: ""
        val userDir = System.getProperty("user.dir") ?: ""

        return activeProfiles.contains("test") ||
            activeProfiles.contains("testcontainers") ||
            classpath.contains("test") ||
            classpath.contains("junit") ||
            classpath.contains("gradle") ||
            userDir.contains("build")
    }
}