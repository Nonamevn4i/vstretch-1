package com.vstretch.app

import android.content.Context
import kotlin.system.exitProcess

/** Chạy trong tiến trình shell do Shizuku khởi tạo (uid 2000). */
class ShellService() : IShell.Stub() {
    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : this()

    override fun destroy() { exitProcess(0) }

    override fun exec(cmd: String): String = try {
        val p = ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out.trim()
    } catch (t: Throwable) {
        "ERR: ${t.message}"
    }
}
