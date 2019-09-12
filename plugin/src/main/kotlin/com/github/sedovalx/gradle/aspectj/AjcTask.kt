package com.github.sedovalx.gradle.aspectj

import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.TrueFileFilter
import org.aspectj.bridge.IMessage
import org.aspectj.bridge.IMessageHolder
import org.aspectj.tools.ajc.Main
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Paths

open class AjcTask : DefaultTask() {
    init {
        logging.captureStandardOutput(LogLevel.INFO)
    }

    lateinit var sourceSet: SourceSet
    lateinit var buildDir: File

    // Task properties
    var source: String = "1.7"
    var target: String = "1.7"
    var writeToLog: Boolean = false

    @TaskAction
    fun compile() {
        logger.info("=".repeat(30))
        logger.info("=".repeat(30))
        logger.info("Running ajc on classpath: $classpath")

        val tempDirectory = Paths.get(buildDir.toPath().toString(), "ajc").toFile()
        if (!tempDirectory.exists()) {
            logger.info("Created temp folder $tempDirectory")
            tempDirectory.mkdirs()
        }
        val logPath = Paths.get(buildDir.toPath().toString(), "ajc.log")

        val ajcParams = arrayOf(
                "-Xset:avoidFinal=true",
                "-Xlint:warning",
                "-inpath",
                sourceSet.output.classesDirs.joinToString(File.pathSeparator) { it.absolutePath },
                "-sourceroots",
                getSourceRoots(),
                "-d",
                tempDirectory.absolutePath,
                "-classpath",
                classpath,
                "-aspectpath",
                classpath,
                "-source",
                this.source,
                "-target",
                this.target,
                "-g:none",
                "-encoding",
                "UTF-8",
                "-time",
                "-warn:constructorName",
                "-warn:packageDefaultMethod",
                "-warn:deprecation",
                "-warn:maskedCatchBlocks",
                "-warn:unusedLocals",
                "-warn:unusedArguments",
                "-warn:unusedImports",
                "-warn:syntheticAccess",
                "-warn:assertIdentifier"
        ).let {
            if (writeToLog) {
                it.plus("-log").plus(logPath.toString()).plus("-showWeaveInfo")
            } else it
        }

        logger.debug("About to run ajc with parameters: \n${ajcParams.toList().joinToString("\t\n")}")

        val currentClasspath =
            (Thread.currentThread().contextClassLoader as? URLClassLoader)?.urLs?.joinToString("\n") { it.path }
        if (currentClasspath != null) {
            logger.debug("Task classpath:\n$currentClasspath")
        }

        val msgHolder = try {
            MsgHolder().apply {
                Main().run(ajcParams, this)
            }
        } catch (ex: Exception) {
            throw GradleException("Error running task", ex)
        }

        val outputDir = sourceSet.output.classesDirs.find { it.name == "java" }?.absoluteFile ?: sourceSet.output.classesDirs.first().absoluteFile
        try {
            logger.info("ajc completed, processing the temp")
            FileUtils.copyDirectory(tempDirectory, outputDir)
            FileUtils.cleanDirectory(tempDirectory)
        } catch (ex: IOException) {
            throw GradleException("Failed to copy files and clean temp", ex)
        }

        if (writeToLog) {
            logger.info("See $logPath for the Ajc log messages")
        } else {
            logger.info("ajc result: %d file(s) processed, %d pointcut(s) woven, %d error(s), %d warning(s)".format(
                    files(outputDir).size,
                    msgHolder.numMessages(IMessage.WEAVEINFO, false),
                    msgHolder.numMessages(IMessage.ERROR, true),
                    msgHolder.numMessages(IMessage.WARNING, false)
            ))

            msgHolder.logIfAny(LogLevel.ERROR, IMessage.ERROR, greater = true)
            msgHolder.logIfAny(LogLevel.WARN, IMessage.WARNING, greater = false)

            if (msgHolder.hasAnyMessage(IMessage.ERROR, greater = true)) {
                throw GradleException("Ajc failed, see messages above. You can run the task with --info or --debug " +
                        "parameters to get more detailed output.")
            }
        }
    }

    private fun files(dir: File): Collection<File> {
        return FileUtils.listFiles(dir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE).filter { it.isFile }
    }

    /**
     * Comma separated absolute paths to folders with aspects
     */
    private fun getSourceRoots(): String = Files.createTempDirectory("aspects").toAbsolutePath().toString()

    private val classpath: String by lazy {
        (sourceSet.compileClasspath + sourceSet.runtimeClasspath).filter { it.exists() }.asPath
    }

    private fun IMessageHolder.logIfAny(logLevel: LogLevel, kind: IMessage.Kind, greater: Boolean) {
        if (hasAnyMessage(kind, greater)) {
            val message = getMessages(kind, greater).joinToString("\n * ", prefix = "\n$logLevel:\n * ")
            logger.log(logLevel, message)
        }
    }
}