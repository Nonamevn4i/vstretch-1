package com.vstretch.app

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

/** Cầu nối Shizuku: bind UserService rồi chạy lệnh shell. Callback luôn về main thread. */
object Shizu {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var svc: IShell? = null
    @Volatile private var binding = false
    private val pending = ArrayList<() -> Unit>()

    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            svc = IShell.Stub.asInterface(binder)
            binding = false
            val jobs = synchronized(pending) { pending.toList().also { pending.clear() } }
            jobs.forEach { io.execute(it) }
        }
        override fun onServiceDisconnected(name: ComponentName) { svc = null; binding = false }
    }

    fun running(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    fun granted(): Boolean = try {
        running() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    fun requestPermission(code: Int) { try { Shizuku.requestPermission(code) } catch (_: Throwable) {} }

    fun ensureBound() {
        if (svc != null || binding || !granted()) return
        binding = true
        try { Shizuku.bindUserService(args, conn) } catch (_: Throwable) { binding = false }
    }

    fun exec(cmd: String, done: (String) -> Unit) {
        if (!granted()) { main.post { done("ERR: Shizuku chưa chạy hoặc chưa cấp quyền") }; return }
        val job: () -> Unit = {
            val r = try { svc?.exec(cmd) ?: "ERR: service chưa sẵn sàng" } catch (t: Throwable) { "ERR: ${t.message}" }
            main.post { done(r) }
            Unit
        }
        if (svc != null) io.execute(job) else { synchronized(pending) { pending.add(job) }; ensureBound() }
    }
}
