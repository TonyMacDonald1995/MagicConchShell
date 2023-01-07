package com.tonymacdonald1995.magicconchshell

import com.theokanning.openai.OpenAiService
import com.theokanning.openai.completion.CompletionRequest
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


fun main(args: Array<String>) {
    val botToken : String = System.getenv("BOTTOKEN") ?: ""
    val openAiToken : String = System.getenv("AITOKEN") ?: ""
    if (botToken.isEmpty()) {
        log("Error: No bot token")
        return
    }
    if (openAiToken.isEmpty()) {
        log("Error: No OpenAI token")
        return
    }
    val magicConchShell = MagicConchShell()
    magicConchShell.openAiService = OpenAiService(openAiToken)
    val jda = JDABuilder.createDefault(botToken).addEventListeners(magicConchShell).build()
    jda.selfUser.manager.setName("Magic Conch Shell").queue()
}
class MagicConchShell : ListenerAdapter() {

    var openAiService : OpenAiService? = null

    override fun onGuildReady(event : GuildReadyEvent) {
        log("Connected to " + event.guild.name)

        event.guild.selfMember.modifyNickname("Magic Conch Shell").queue()
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.message.mentions.isMentioned(event.guild.selfMember)) {
            event.message.channel.asTextChannel().sendTyping().queue()
            val completionRequest = CompletionRequest.builder()
                .prompt(event.message.contentDisplay.removePrefix("@Magic Conch Shell "))
                .model("text-davinci-003")
                .echo(false)
                .maxTokens(1000)
                .build()
            openAiService!!.createCompletion(completionRequest).choices.forEach {
                event.message.channel.asTextChannel().sendMessage(it.text).queue()
            }
        }
    }
}

fun log(message : String) {
    println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " + message)
}