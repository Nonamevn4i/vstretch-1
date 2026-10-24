package com.vstretch.app

import android.content.Context
import kotlin.system.exitProcess

/** Chạy trong tiến trình shell do Shizuku khởi tạo (uid 2000). */
class ShellService() : IShell.Stub() {
    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : this()

    override fun destroy() { exitProcess(0) }

    override fun setStretch(lnW: Int, lnH: Int, physW: Int, physH: Int, fill: Boolean): String = try {
        Sf.set(lnW, lnH, physW, physH, fill)
    } catch (t: Throwable) {
        "ERR: ${t.javaClass.simpleName}: ${t.message}"
    }

    override fun diag(): String = try { Sf.diag() } catch (t: Throwable) { "ERR: ${t.message}" }

    override fun exec(cmd: String): String = try {
        val p = ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out.trim()
    } catch (t: Throwable) {
        "ERR: ${t.message}"
    }
}
