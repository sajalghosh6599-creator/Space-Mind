package com.strictmode.app

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("strict_prefs", Context.MODE_PRIVATE)

    // ---------- PIN / password (hash kore save hoy, asol PIN save hoy na) ----------
    fun isSetupDone(c: Context) = sp(c).contains("hash")

    /** mode: pin4, pin6, pin8, pin10 ba password */
    fun mode(c: Context): String = sp(c).getString("mode", "pin4") ?: "pin4"

    fun setSecret(c: Context, secret: String, mode: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp(c).edit()
            .putString("mode", mode)
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", hash(secret, salt))
            .apply()
    }

    fun checkSecret(c: Context, secret: String): Boolean {
        val s = sp(c)
        val salt = Base64.decode(s.getString("salt", null) ?: return false, Base64.NO_WRAP)
        val expected = s.getString("hash", null) ?: return false
        return MessageDigest.isEqual(hash(secret, salt).toByteArray(), expected.toByteArray())
    }

    private fun hash(secret: String, salt: ByteArray): String {
        val spec = PBEKeySpec(secret.toCharArray(), salt, 120_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // ---------- Setup complete (shob permission dewar por true hoy) ----------
    fun isSetupComplete(c: Context) = sp(c).getBoolean("setup_complete", false)
    fun setSetupComplete(c: Context, v: Boolean) = sp(c).edit().putBoolean("setup_complete", v).apply()

    fun flag(c: Context, key: String) = sp(c).getBoolean("flag_$key", false)
    fun setFlag(c: Context, key: String, v: Boolean) = sp(c).edit().putBoolean("flag_$key", v).apply()

    // ---------- My Space (je app gulo Space e add kora hoyeche) ----------
    fun spaceApps(c: Context): Set<String> = sp(c).getStringSet("space_apps", null)?.toSet() ?: emptySet()

    fun setSpaceApp(c: Context, pkg: String, on: Boolean) {
        val s = spaceApps(c).toMutableSet()
        if (on) s.add(pkg) else s.remove(pkg)
        sp(c).edit().putStringSet("space_apps", s).apply()
    }

    // ---------- Lock Mode (Home/Recents chaple Space Mind e fire ashe) ----------
    fun isLockMode(c: Context) = sp(c).getBoolean("lock_mode", false)
    fun setLockMode(c: Context, on: Boolean) = sp(c).edit().putBoolean("lock_mode", on).apply()

    // ---------- Space Mind on/off ----------
    fun isStrict(c: Context) = sp(c).getBoolean("strict", true)
    fun setStrict(c: Context, on: Boolean) = sp(c).edit().putBoolean("strict", on).apply()

    // ---------- AI preprocess mode ----------
    fun preprocess(c: Context): String = sp(c).getString("pre", "bgr_mean") ?: "bgr_mean"
    fun setPreprocess(c: Context, v: String) = sp(c).edit().putString("pre", v).apply()

    // ---------- Trusted list (AI check hobe na) ----------
    fun trusted(c: Context): Set<String> =
        sp(c).getStringSet("trusted", null)?.toSet() ?: setOf("com.whatsapp", "com.whatsapp.w4b")

    fun isTrusted(c: Context, pkg: String) = pkg == c.packageName || pkg in trusted(c)

    fun setTrusted(c: Context, pkg: String, on: Boolean) {
        val s = trusted(c).toMutableSet()
        if (on) s.add(pkg) else s.remove(pkg)
        sp(c).edit().putStringSet("trusted", s).apply()
        if (on) setBlocked(c, pkg, false) // approve korle unblock-o hoy
    }

    // ---------- Blocked list (AI porn dhorle ekhane jabe) ----------
    fun blocked(c: Context): Set<String> = sp(c).getStringSet("blocked", null)?.toSet() ?: emptySet()

    fun isBlocked(c: Context, pkg: String) = pkg in blocked(c)

    fun setBlocked(c: Context, pkg: String, on: Boolean) {
        val s = blocked(c).toMutableSet()
        if (on) s.add(pkg) else s.remove(pkg)
        sp(c).edit().putStringSet("blocked", s).apply()
    }
}
