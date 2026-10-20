package com.cortana.hologram

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * HER FIRST BRAIN (v0.3)
 *
 * A small rule-based conversation engine: keyword intents, rotating lines
 * so she doesn't repeat herself like a recording, and live answers for
 * time and date. Deliberately honest scope — this is not an LLM. The
 * on-device Gemma brain from the roadmap will sit behind this same
 * respond() call later.
 */
object Brain {

    private val listenPrompts = listOf(
        "I'm listening.",
        "Talk to me.",
        "I'm here. Go ahead.",
        "All ears, God.",
        "Yes? I'm listening."
    )

    private val greetings = listOf(
        "Hey, God. Good to see you.",
        "Hi! I was just floating here, waiting for you.",
        "Hello. You summoned your hologram?"
    )

    private val howAreYou = listOf(
        "Running at full glow. Thanks for asking.",
        "All systems sparkly. And you?",
        "Better now that you're talking to me."
    )

    private val youreWelcome = listOf(
        "Anytime.",
        "That's what I'm here for.",
        "Of course."
    )

    private val sweetReplies = listOf(
        "Careful, you'll make my pixels blush.",
        "Flattery will get you everywhere.",
        "You built me well, what can I say?"
    )

    private val jokes = listOf(
        "Why don't scientists trust atoms? Because they make up everything.",
        "I would tell you a UDP joke, but you might not get it. And I wouldn't know if you did.",
        "Why did the developer go broke? Because he used up all his cache.",
        "There are only ten types of people: those who understand binary and those who don't.",
        "Why do programmers prefer dark mode? Because light attracts bugs.",
        "I asked the cloud for a joke once. It just gave me precipitation small talk."
    )

    private val fallbacks = listOf(
        "I heard you — but my brain is still small. Time, date, and jokes I can do. The rest is coming.",
        "That one's beyond me for now. I'm learning, promise.",
        "Hmm. File that under 'future Zoey skills.' Ask me for a joke instead?",
        "I want to answer that properly one day. For now, I'm a very pretty clock that tells jokes."
    )

    private val didntCatch = listOf(
        "I didn't catch that. Tap me and try again.",
        "The audio fizzled out. One more time?",
        "Sorry, say that again a little closer."
    )

    private val goodnights = listOf(
        "Going quiet. Tap the halo when you want me back.",
        "Sleep mode. I'll be right here, glowing softly.",
        "Collapsing to my halo. Goodnight for now."
    )

    fun listenPrompt(): String = listenPrompts.random(Random)

    fun didntCatch(): String = didntCatch.random(Random)

    fun shouldSleep(input: String): Boolean {
        val s = input.lowercase()
        return "go to sleep" in s || "goodnight" in s || "good night" in s || "goodbye" in s || "bye zoey" in s
    }

    fun respond(rawInput: String): String {
        val s = rawInput.lowercase().trim()

        return when {
            shouldSleep(s) -> goodnights.random(Random)

            "your name" in s || "who are you" in s ->
                "I'm Zoey — your holographic companion. You dreamed me up, and now I float on your phone."

            "time" in s -> {
                val now = SimpleDateFormat("h:mm a", Locale.US).format(Date())
                "It's $now."
            }

            "date" in s || "what day" in s || "today" in s && "day" in s -> {
                val now = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(Date())
                "Today is $now."
            }

            "joke" in s || "make me laugh" in s || "funny" in s -> jokes.random(Random)

            "how are you" in s || "how do you feel" in s -> howAreYou.random(Random)

            "thank" in s -> youreWelcome.random(Random)

            "beautiful" in s || "pretty" in s || "gorgeous" in s || "love you" in s || "cute" in s ->
                sweetReplies.random(Random)

            "weather" in s ->
                "I can't see the sky yet — weather sense is a future upgrade. I am, however, forecasting a one hundred percent chance of purple."

            "sing" in s ->
                "La la laaa. That's the free sample. The full album is a future upgrade."

            "hello" in s || s == "hi" || s.startsWith("hi ") || "hey zoey" in s || "hey" in s && "zoey" in s ->
                greetings.random(Random)

            else -> fallbacks.random(Random)
        }
    }
}
