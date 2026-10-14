package de.j4velin.contracts.config

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Component

/**
 * Information about the running app, shown in the footer of every page. The templates read it as the bean
 * `@appInfo`, so that it is available on all pages, including the ones without a controller (like `/about`)
 *
 * @param buildProperties the build information Gradle writes into the jar (see `springBoot.buildInfo` in
 * build.gradle.kts). Missing, if the app runs without it, for example from an IDE build
 */
@Component("appInfo")
class AppInfo(buildProperties: ObjectProvider<BuildProperties>) {

    /**
     * The version of the running app: the release tag (e.g. "1.2.0") for released images, see build.gradle.kts
     */
    val version: String = buildProperties.ifAvailable?.version ?: "dev"

    val repositoryUrl = "https://github.com/j4velin/ContractsManagementSystem"
}
