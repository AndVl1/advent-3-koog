package ru.andvl.chatter.backend

import io.github.cdimascio.dotenv.Dotenv
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val dotenv = Dotenv.configure()
//        .directory("../")
        .ignoreIfMissing()
        .load()

    // Set environment variables from .env
    dotenv["GOOGLE_API_KEY"]?.let { System.setProperty("GOOGLE_API_KEY", it) }
    dotenv["OPENROUTER_API_KEY"]?.let { System.setProperty("OPENROUTER_API_KEY", it) }
    dotenv["OPENAI_API_KEY"]?.let { System.setProperty("OPENAI_API_KEY", it) }
    configureFrameworks()
    configureSerialization()
    configureRouting()
}