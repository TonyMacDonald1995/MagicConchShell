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
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.utils.FileUpload
import java.io.BufferedReader
import java.io.DataOutputStream
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
    val magicConchShell = MagicConchShell()
    magicConchShell.openAiToken = openAiToken
    magicConchShell.openAiService = OpenAiService(openAiToken)
    val jda = JDABuilder.createDefault(botToken).addEventListeners(magicConchShell).build()
    jda.selfUser.manager.setName("Magic Conch Shell").queue()
}
class MagicConchShell : ListenerAdapter() {

    private val playerManager = DefaultAudioPlayerManager()
    private val audioPlayers = mutableMapOf<Long, AudioPlayer>()

    var openAiToken: String? = null
    var openAiService: OpenAiService? = null

    init {
        AudioSourceManagers.registerRemoteSources(playerManager)
    }

    override fun onGuildReady(event: GuildReadyEvent) {
        log("Connected to " + event.guild.name)

        event.guild.selfMember.modifyNickname("Magic Conch Shell").queue()
        event.guild.upsertCommand("image", "Generates an image based on the prompt given")
            .addOption(OptionType.STRING, "prompt", "Prompt for image generation", true)
            .queue()
        event.guild.upsertCommand("join", "This is a test").queue()
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.guild == null)
            return

        when (event.name) {
            "image" -> genImage(event)
            "join" -> joinVoice(event)
            else -> event.reply("Error: Unknown command").setEphemeral(true).queue()
        }
    }

    private fun joinVoice(event: SlashCommandInteractionEvent) {
        val member = event.member ?: return

        if (member.voiceState?.channel == null)
            event.reply("You aren't in a voice channel.")
        event.reply("You have selected Microsoft Sam as the computer's default voice.")

        val voiceChannel = member.voiceState?.channel?.asVoiceChannel() ?: return
        playMicrosoftSam(voiceChannel)

    }

    private fun playMicrosoftSam(voiceChannel: VoiceChannel) {
        val guild = voiceChannel.guild
        val audioManager = guild.audioManager

        if (!audioManager.isConnected)
            audioManager.openAudioConnection(voiceChannel)

        val player = playerManager.createPlayer()
        audioPlayers[guild.idLong] = player
        audioManager.sendingHandler = AudioHandler(player)

        val trackUrl = "https://www.youtube.com/watch?v=P5DXddc5hOw"
        playerManager.loadItem(trackUrl, object : AudioLoadResultHandler {
            override fun trackLoaded(track: AudioTrack?) {
                player.playTrack(track)
            }

            override fun playlistLoaded(playlist: AudioPlaylist?) {}

            override fun noMatches() {}

            override fun loadFailed(exception: FriendlyException?) {}

        })
        Thread {
            while (player.playingTrack == null) {}
            while (player.playingTrack != null) {}
            audioManager.closeAudioConnection()
        }.start()
    }

    private fun genImage(event: SlashCommandInteractionEvent) {
        event.deferReply(true).queue()
        val hook = event.hook
        hook.setEphemeral(true)
        val member = event.user.asMention
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
            var line: String?
            while (br.readLine().also { line = it } != null) {
                val matcher = pattern.matcher(line.toString())
                if (matcher.find())
                    output = matcher.group(1)
            }
        }
        hook.sendMessage("Done").queue()
        channel.sendMessage("$member requested this image using the prompt: \"$prompt\"")
            .addFiles(FileUpload.fromData(URL(output).openStream(), "image.png")).queue()
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

class AudioHandler(private val player: AudioPlayer) : AudioSendHandler {
    private var lastFrame: AudioFrame? = null

    override fun canProvide(): Boolean {
        lastFrame = player.provide()
        return lastFrame != null
    }

    override fun provide20MsAudio(): ByteBuffer? {
        return ByteBuffer.wrap(lastFrame?.data ?: ByteArray(0))
    }

    override fun isOpus(): Boolean {
        return true
    }
}

fun log(message : String) {
    println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " + message)
}