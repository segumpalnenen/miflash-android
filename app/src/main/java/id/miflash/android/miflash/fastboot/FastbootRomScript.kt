package id.miflash.android.miflash.fastboot

sealed interface FastbootStep {
    data class Flash(val partition: String, val filename: String) : FastbootStep
    data class Erase(val partition: String) : FastbootStep
    data class Reboot(val target: String?) : FastbootStep
    data class Oem(val args: String) : FastbootStep
    data class SetActive(val slot: String) : FastbootStep
    data class FlashingLock(val lock: Boolean) : FastbootStep
    data class GetVar(val name: String) : FastbootStep
    data class Sleep(val seconds: Int) : FastbootStep
}

object FastbootRomScript {

    fun parse(text: String, isBat: Boolean): List<FastbootStep> {
        val steps = mutableListOf<FastbootStep>()
        for (rawLine in text.lines()) {
            var line = rawLine.trim()
            if (line.isEmpty()) continue
            if (isBat) {
                if (line.startsWith("::")) continue
                val lower = line.lowercase()
                if (lower.startsWith("rem ")) continue
                if (lower == "@echo off" || lower.startsWith("@echo") || lower.startsWith("echo ")) continue
                if (lower.startsWith("set ") || lower.startsWith("call ") || lower.startsWith("if ")) continue
                if (lower.startsWith("exit") || lower.startsWith("goto ")) break
                if (lower.startsWith("pause")) continue
                if (lower.startsWith("timeout ")) {
                    parseTimeoutBat(line)?.let { steps.add(it) }
                    continue
                }
                if (lower.startsWith("ping ")) continue
            } else {
                if (line.startsWith("#")) continue
                if (line.startsWith("set ") || line.startsWith("echo ")) continue
                if (line.startsWith("if ")) continue
                if (line.startsWith("sleep ")) {
                    line.substringAfter("sleep").trim().toIntOrNull()?.let {
                        steps.add(FastbootStep.Sleep(it.coerceIn(1, 30)))
                    }
                    continue
                }
            }
            line = line.substringBefore("||").substringBefore("&&").trim()
            val pipeIndex = line.indexOf('|')
            if (pipeIndex >= 0) line = line.substring(0, pipeIndex).trim()
            if (line.isEmpty()) continue
            steps.addAll(parseFastbootLine(line))
        }
        return steps
    }

    private fun parseTimeoutBat(line: String): FastbootStep.Sleep? {
        val match = Regex("/t\\s+(\\d+)").find(line.lowercase()) ?: return null
        val seconds = match.groupValues[1].toIntOrNull() ?: return null
        return FastbootStep.Sleep(seconds.coerceIn(1, 30))
    }

    private fun parseFastbootLine(line: String): List<FastbootStep> {
        val tokens = tokenize(line)
        if (tokens.isEmpty()) return emptyList()
        val first = tokens[0].lowercase()
        if (first != "fastboot" && first != "fastboot.exe") return emptyList()
        var i = 1
        var wipe = false
        while (i < tokens.size) {
            val token = tokens[i]
            when {
                token == "\$*" || token == "%*" -> i++
                token == "-s" -> i += 2
                token.startsWith("-s") && token.length > 2 -> i++
                token == "--slot" -> i += 2
                token.startsWith("--slot=") -> i++
                token == "--disable-verity" || token == "--disable-verification" -> i++
                token == "-w" -> {
                    wipe = true
                    i++
                }
                else -> break
            }
        }
        if (i >= tokens.size) {
            return if (wipe) listOf(FastbootStep.Erase("userdata"), FastbootStep.Erase("cache")) else emptyList()
        }
        val step: FastbootStep? = when (val command = tokens[i].lowercase()) {
            "flash" -> parseFlash(tokens, i + 1)
            "erase" -> tokens.getOrNull(i + 1)?.let { FastbootStep.Erase(it) }
            "format" -> tokens.getOrNull(i + 1)?.let { FastbootStep.Erase(it) }
            "reboot" -> FastbootStep.Reboot(tokens.getOrNull(i + 1)?.lowercase())
            "reboot-bootloader" -> FastbootStep.Reboot("bootloader")
            "reboot-fastboot" -> FastbootStep.Reboot("fastboot")
            "reboot-recovery" -> FastbootStep.Reboot("recovery")
            "oem" -> FastbootStep.Oem(tokens.drop(i + 1).joinToString(" "))
            "flashing" -> when (tokens.getOrNull(i + 1)?.lowercase()) {
                "lock" -> FastbootStep.FlashingLock(true)
                "unlock" -> FastbootStep.FlashingLock(false)
                else -> null
            }
            "set_active" -> tokens.getOrNull(i + 1)?.let { FastbootStep.SetActive(it) }
            "getvar" -> tokens.getOrNull(i + 1)?.let { FastbootStep.GetVar(it) }
            "--set-active" -> tokens.getOrNull(i + 1)?.let { FastbootStep.SetActive(it) }
            else -> if (command.startsWith("--set-active=")) {
                FastbootStep.SetActive(command.substringAfter("="))
            } else {
                null
            }
        }
        val out = mutableListOf<FastbootStep>()
        if (wipe) {
            out.add(FastbootStep.Erase("userdata"))
            out.add(FastbootStep.Erase("cache"))
        }
        if (step != null) out.add(step)
        return out
    }

    private fun parseFlash(tokens: List<String>, start: Int): FastbootStep.Flash? {
        var i = start
        while (i < tokens.size) {
            val token = tokens[i]
            if (token.startsWith("--")) {
                i += if (token.contains("=")) 1 else 2
                continue
            }
            break
        }
        val partition = tokens.getOrNull(i) ?: return null
        val file = tokens.getOrNull(i + 1) ?: return null
        return FastbootStep.Flash(partition, fileNameOnly(file))
    }

    fun fileNameOnly(path: String): String {
        val cleaned = path
            .replace("%~dp0", "")
            .replace("\$PWD/", "")
            .trim('"', '\'')
        val slash = maxOf(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'))
        return if (slash >= 0) cleaned.substring(slash + 1) else cleaned
    }

    private fun tokenize(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch.isWhitespace() && !inQuotes -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) tokens.add(current.toString())
        return tokens
    }
}
