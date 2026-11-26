package com.school.pendrivesync

import android.os.StatFs
import java.io.File

object Syncer {
    // Only OS junk folders are skipped. Everything else on the pendrive is mirrored, folders included.
    private val skipDirs = setOf("system volume information", "lost.dir", "android", "\$recycle.bin")

    private class Counts { var files = 0; var dirs = 0; var failed = 0 }

    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9 _-]"), "_").trim().ifEmpty { "drive" }

    private fun copyAtomic(from: File, to: File) {
        to.parentFile?.mkdirs()
        val tmp = File(to.path + ".part")
        from.copyTo(tmp, overwrite = true)
        if (!tmp.renameTo(to)) { tmp.copyTo(to, overwrite = false); tmp.delete() }
    }

    private fun versioned(f: File, stamp: Long): File {
        val ext = if (f.extension.isEmpty()) "" else "." + f.extension
        return File(f.parentFile, "${f.nameWithoutExtension}_$stamp$ext")
    }

    /** Mirrors srcDir into destDir folder by folder, so the teacher's folder structure is kept exactly. */
    private fun mirror(srcDir: File, destDir: File, rel: String, uuid: String, store: Store, c: Counts) {
        destDir.mkdirs()
        val kids = srcDir.listFiles() ?: return
        for (k in kids.sortedBy { it.name }) {
            if (k.name.startsWith(".")) continue
            val childRel = if (rel.isEmpty()) k.name else "$rel/${k.name}"
            if (k.isDirectory) {
                if (k.name.lowercase() in skipDirs) continue
                c.dirs++
                mirror(k, File(destDir, k.name), childRel, uuid, store, c)
            } else if (k.isFile) {
                try {
                    if (store.has(uuid, childRel, k.length(), k.lastModified())) continue
                    var out = File(destDir, k.name)
                    if (out.exists() && out.length() != k.length()) out = versioned(out, k.lastModified())
                    if (!out.exists()) { copyAtomic(k, out); c.files++ }
                    store.add(uuid, childRel, k.length(), k.lastModified())
                } catch (e: Exception) {
                    c.failed++
                    AppLog.add("Failed: $childRel (${e.message})")
                }
            }
        }
    }

    /** Teacher pendrive -> <save location>/Collected/<label>_<id>/ (whole drive, one folder). */
    fun importFrom(src: File, uuid: String, label: String, store: Store): String {
        val top = src.listFiles() ?: emptyArray()
        AppLog.add("Found on drive: ${top.count { it.isDirectory }} folder(s), ${top.count { it.isFile }} file(s) at top level")
        val dest = File(store.collected(), "${safe(label)}_$uuid")
        val c = Counts()
        mirror(src, dest, "", uuid, store, c)
        return "${c.files} new file(s), ${c.dirs} folder(s) scanned" + if (c.failed > 0) ", ${c.failed} failed" else ""
    }

    /** Everything collected -> <master>/StudyMaterials/ with all folders. Returns files copied, or -1 if no space. */
    fun exportTo(master: File, store: Store): Int {
        val src = store.collected()
        if (!src.exists()) return 0
        val destRoot = File(master, "StudyMaterials")
        val todo = src.walkTopDown().filter { it.isFile && !it.name.endsWith(".part") }.filter {
            val o = File(destRoot, it.relativeTo(src).path)
            !(o.exists() && o.length() == it.length())
        }.toList()
        val need = todo.sumOf { it.length() }
        val free = try { StatFs(master.path).availableBytes } catch (_: Exception) { Long.MAX_VALUE }
        if (need > free) return -1
        src.walkTopDown().filter { it.isDirectory }.forEach { File(destRoot, it.relativeTo(src).path).mkdirs() }
        var n = 0
        todo.forEach { f ->
            try {
                copyAtomic(f, File(destRoot, f.relativeTo(src).path))
                n++
            } catch (e: Exception) {
                AppLog.add("Failed: ${f.name} (${e.message})")
            }
        }
        return n
    }
}
