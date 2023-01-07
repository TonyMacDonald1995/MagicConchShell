package com.tonymacdonald1995.magicconchshell

import com.theokanning.openai.OpenAiService
import com.theokanning.openai.completion.CompletionRequest
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.OptionType
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern


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
    magicConchShell.openAiToken = openAiToken
    magicConchShell.openAiService = OpenAiService(openAiToken)
    val jda = JDABuilder.createDefault(botToken).addEventListeners(magicConchShell).build()
    jda.selfUser.manager.setName("Magic Conch Shell").queue()
}
class MagicConchShell : ListenerAdapter() {

    var openAiToken : String? = null
    var openAiService : OpenAiService? = null

    override fun onGuildReady(event : GuildReadyEvent) {
        log("Connected to " + event.guild.name)

        event.guild.selfMember.modifyNickname("Magic Conch Shell").queue()
        event.guild.upsertCommand("image", "Generates an image based on the prompt given")
            .addOption(OptionType.STRING, "prompt", "Prompt for image generation", true)
            .queue()
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.guild == null)
            return

        when(event.name) {
            "image" -> genImage(event)
            else -> event.reply("Error: Unknown command").setEphemeral(true).queue()
        }
    }

    private fun genImage(event : SlashCommandInteractionEvent) {
        event.deferReply(true).queue()
        val hook = event.hook
        hook.setEphemeral(true)
        val member =  event.user.asMention
        val channel = event.channel
        val prompt = event.getOption("prompt")?.asString ?: return

        val url = URL("https://api.openai.com/v1/images/generations")
        val postData = "{" +
                "\"prompt\": \"$prompt\"," +
                "\"n\": 1," +
                "\"size\": \"1024x1024\"" +
                "}"

        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $openAiToken")
        conn.useCaches = false

        DataOutputStream(conn.outputStream).use { it.writeBytes(postData) }
        var output = ""
        val pattern = Pattern.compile("\"url\": \"([^\"]*)\"")
        BufferedReader(InputStreamReader(conn.inputStream)).use { br ->
            var line : String?
            while (br.readLine().also { line = it } != null) {
                val matcher = pattern.matcher(line.toString())
                if (matcher.find())
                    output = matcher.group(1)
            }
        }
        hook.sendMessage("Done").queue()
        channel.sendMessage("$member requested this image using the prompt: \"$prompt\"").queue()
        channel.sendMessage(output).queue()

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