package com.tonymacdonald1995.magicconchshell

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame
import com.theokanning.openai.OpenAiService
import com.theokanning.openai.completion.CompletionRequest
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.audio.AudioSendHandler
import net.dv8tion.jda.api.entities.Icon
import net.dv8tion.jda.api.entities.MessageHistory
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.FileUpload
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
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
    val magicConchShell = MagicConchShell(openAiToken)
    val jda = JDABuilder.createDefault(botToken).addEventListeners(magicConchShell).enableIntents(GatewayIntent.MESSAGE_CONTENT).build()
    jda.selfUser.manager.setName("Tod").queue()
}
class MagicConchShell(private val openAiToken: String) : ListenerAdapter() {

    private val openAiService: OpenAiService = OpenAiService(openAiToken)

    private var messageHistory : MutableList<String> = mutableListOf()

    override fun onGuildReady(event: GuildReadyEvent) {
        log("Connected to " + event.guild.name)

        event.jda.selfUser.manager.setAvatar(Pfp.pfp)
        event.guild.selfMember.modifyNickname("Tod").queue()
        event.guild.upsertCommand("reset-tod", "Reset Tod's memory").setDefaultPermissions(DefaultMemberPermissions.DISABLED).queue()
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.guild == null)
            return

        when (event.name) {
            "reset-tod" -> resetTod(event)
            else -> event.reply("Error: Unknown command").setEphemeral(true).queue()
        }
    }

    private fun resetTod(event: SlashCommandInteractionEvent) {
        if (event.channel.id != "1075951768564928593") {
            event.reply("This command can only be used in " + event.guild?.getTextChannelById("1075951768564928593")?.asMention).setEphemeral(true).queue()
            return
        }

        messageHistory = mutableListOf()
        event.reply("Tod's memory has been reset.").queue()
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.message.channel.id == "1075951768564928593" && event.message.member != event.guild.selfMember) {
            event.message.channel.asTextChannel().sendTyping().queue()
            messageHistory.add("[${event.message.member?.nickname}] ${event.message.contentDisplay}")
            log("[messageHistory] [${event.message.member?.nickname}] ${event.message.contentDisplay}")
            var prompt = "You are an anthropomorphic fox named Tod, participating is a group chat. You like to use rude an abrasive language and thrive in an environment of like-minded adults. You have no problem with cursing or foul language and use it regularly. Here is the chat history:\n"
            for (message in messageHistory)
                prompt += "$message\n"
            prompt += "[Tod] "
            val completionRequest = CompletionRequest.builder()
                .prompt(prompt)
                .model("text-davinci-003")
                .echo(false)
                .maxTokens(2048)
                .stop(mutableListOf("\n"))
                .build()
            openAiService!!.createCompletion(completionRequest).choices.forEach {
                if (it.text.isEmpty())
                    onMessageReceived(event)
                event.message.channel.asTextChannel().sendMessage(it.text).queue()
                messageHistory.add("[Tod] ${it.text}")
                log("[messageHistory] [Tod] ${it.text}")
            }
        }
    }

}

fun log(message : String) {
    println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " + message)
}