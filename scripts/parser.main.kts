#!/usr/bin/env kotlin

@file:DependsOn(
    // Clikt (CLI framework)
    "com.github.ajalt.clikt:clikt-jvm:5.0.2",

    // kotlin-csv + Kotlin-Grass (CSV parsing)
    "com.jsoizo:kotlin-csv-jvm:1.10.0",
    "io.github.blackmo18:kotlin-grass-core-jvm:1.0.0",
    "io.github.blackmo18:kotlin-grass-parser-jvm:0.8.0",

    // Exposed (SQL framework) + JDBC drivers
    "org.jetbrains.exposed:exposed-core:0.57.0",
    "org.jetbrains.exposed:exposed-jdbc:0.57.0",
    "org.xerial:sqlite-jdbc:3.47.1.0",

    // Slack API Client
    "com.slack.api:slack-api-client:1.44.2",
)

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.slack.api.Slack
import io.blackmo18.kotlin.grass.dsl.grass
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

// Util functions

fun <T : Table, R> T.withTransaction(statement: T.() -> R): R =
    transaction { statement(this@withTransaction) }

fun ParameterHolder.requiredFileOption(help: String) = option(help = help)
    .file(
        mustExist = true,
        mustBeReadable = true,
        canBeDir = false,
    ).required()

fun File.readMap(): Map<String, String> = buildMap {
    readLines().forEach { line ->
        put(
            key = line.substringBefore(':'),
            value = line.substringAfter(':'),
        )
    }
}

fun Map<String, String>.safeGet(key: String) = getOrDefault(key, key)

// Data model

data class Response(
    val date: String,
    val skuId: String,
    val response: String,
)

object DB : Table() {
    val date = text("date")
    val skuId = text("skuId")
    val response = text("response")
    val duplicates = integer("duplicates")

    private val id = integer("id").autoIncrement()
    override val primaryKey get() = PrimaryKey(id)
}

// Main parser class

class CancellationResponsesParser : CliktCommand() {
    private val csvPath: File by requiredFileOption(
        help = "Path to the CSV file with cancellation reasons",
    )

    private val dbPath: String by option(
        help = "Path to the SQLite database to store cancellation reasons",
    ).required()

    private val packageName: String by option(
        help = "App package name associated with cancellation reasons",
    ).required()

    private val packageMapPath: File by requiredFileOption(
        help = "Path to the package map",
    )

    private val skuMapPath: File by requiredFileOption(
        help = "Path to the SKU map",
    )

    private val slackChannelMapPath: File by requiredFileOption(
        help = "Path to the Slack channel map",
    )

    private val slackApiToken: String by option(
        help = "Token for the Slack app/bot to send messages with",
    ).required()

    private val appName by lazy { packageMapPath.readMap().safeGet(packageName) }
    private val skuMap by lazy { skuMapPath.readMap() }
    private val slackChannelName by lazy {
        slackChannelMapPath.readMap().getOrElse(packageName) {
            throw IllegalArgumentException("No Slack channel name for $packageName")
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun parseResponsesFromCSV() = grass<Response> {
        ignoreUnknownFields = true // intentionally omitting "Country"
        customKeyMapDataProperty = mapOf(
            "Cancellation Date" to Response::date,
            "Sku Id" to Response::skuId,
            "Response" to Response::response,
        )
    }
        .harvest(seed = csvReader().readAllWithHeader(csvPath))
        .toSet()

    private fun getResponsesToNotify(csvResponses: Set<Response>) = buildList {
        Database.connect(
            url = "jdbc:sqlite:$dbPath",
            driver = "org.sqlite.JDBC",
        )

        DB.withTransaction {
            SchemaUtils.create(this)

            val dbResponses = selectAll().map { result ->
                Response(
                    date = result[date],
                    skuId = result[skuId],
                    response = result[response],
                )
            }.toSet()

            (csvResponses - dbResponses).forEach { newResponse ->
                val isDuplicate = selectAll()
                    .where { response eq newResponse.response }
                    .firstOrNull() != null

                insert { statement ->
                    statement[date] = newResponse.date
                    statement[skuId] = newResponse.skuId
                    statement[response] = newResponse.response
                    statement[duplicates] = 0
                }.also {
                    val logPrefix = when {
                        it.insertedCount == 0 -> "Failed to insert response"
                        isDuplicate -> "Inserted duplicate response"
                        else -> {
                            add(newResponse)
                            "Inserted new response"
                        }
                    }

                    println("$logPrefix: $newResponse")
                }
            }
        }
    }

    private suspend fun sendResponsesToSlack(responsesToNotify: List<Response>) {
        // Slack API docs recommend a max message length of 4000 characters
        val maxLength = 4000
        var messageNumber = 0
        var builder = StringBuilder().apply {
            appendLine("*New Google Play Cancellation Responses for $appName*")
        }

        val dispatch = {
            Slack.getInstance()
                .methods(slackApiToken)
                .chatPostMessage { request ->
                    request
                        .channel(slackChannelName)
                        .text(builder.toString().also(::println))
                }.let { response ->
                    if (response.isOk) {
                        println("Sent message #${++messageNumber} to $slackChannelName, length ${builder.length} of $maxLength")
                    }
                }
        }

        responsesToNotify.forEach { response ->
            val responseText = buildString {
                appendLine()
                appendLine("*Date:* ${response.date}")
                appendLine("*SKU:* ${skuMap.safeGet(response.skuId)}")
                appendLine("*Response:* ${response.response.trim()}")
            }

            if ((builder.length + responseText.length) >= maxLength) {
                dispatch()
                delay(1000)
                builder = StringBuilder(responseText)
            } else {
                builder.append(responseText)
            }
        }

        dispatch()
    }

    override fun run() = runBlocking {
        println("Parsing responses for $appName ($packageName)")
        getResponsesToNotify(
            csvResponses = parseResponsesFromCSV()
        ).ifEmpty {
            println("No new responses to notify")
            return@runBlocking
        }.also { responsesToNotify ->
            println("Notifying Slack of ${responsesToNotify.size} new responses")
            sendResponsesToSlack(responsesToNotify)
        }
    }
}

CancellationResponsesParser().main(args)
