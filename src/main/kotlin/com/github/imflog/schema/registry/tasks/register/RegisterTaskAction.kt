package com.github.imflog.schema.registry.tasks.register

import com.github.imflog.schema.registry.LoggingUtils.infoIfNotQuiet
import com.github.imflog.schema.registry.Subject
import com.github.imflog.schema.registry.parser.SchemaParser
import com.github.imflog.schema.registry.toSchemaType
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.gradle.api.logging.Logging
import java.io.File


class RegisterTaskAction(
    private val client: SchemaRegistryClient,
    private val rootDir: File,
    private val subjects: List<Subject>,
    outputDir: String?,
    private val failFast: Boolean = false,
) {

    private val logger = Logging.getLogger(RegisterTaskAction::class.java)
    private val outputFile = outputDir?.let {
        rootDir.resolve(it).resolve("registered.csv")
    }

    fun run(): Int {
        var errorCount = 0
        writeOutputFileHeader()
        subjects.forEach { subject ->
            try {
                val schemaId = registerSchema(subject)
                writeRegisteredSchemaOutput(subject.inputSubject, subject.file, schemaId)
            } catch (e: Exception) {
                logger.error("Could not register schema for '$subject'", e)
                if (failFast) {
                    throw e
                }
                errorCount++
            }
        }
        return errorCount
    }

    private fun registerSchema(
        subject: Subject
    ): Int {
        val parsedSchema = SchemaParser
            .provide(subject.type.toSchemaType(), client, rootDir)
            .parseSchemaFromFile(subject)
        logger.infoIfNotQuiet("Registering $subject (from $subject.file)")
        val schemaId = client.register(subject.inputSubject, parsedSchema, subject.normalize)
        logger.infoIfNotQuiet("$subject (from $subject.file) has been registered with id $schemaId")
        return schemaId
    }

    private fun writeOutputFileHeader() {
        if (subjects.isNotEmpty() && outputFile != null) {
            outputFile.writeText("subject, path, id\n")
        }
    }

    private fun writeRegisteredSchemaOutput(subject: String, path: String, schemaId: Int) {
        outputFile?.appendText("$subject, $path, $schemaId\n")
    }
}
