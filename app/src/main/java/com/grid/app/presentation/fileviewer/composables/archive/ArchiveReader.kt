package com.grid.app.presentation.fileviewer.composables.archive

import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.BufferedInputStream
import java.util.*

class ArchiveReader {
    
    suspend fun readArchive(file: File): Result<List<ArchiveEntry>> = withContext(Dispatchers.IO) {
        try {
            val format = ArchiveFormat.fromFileName(file.name)
            val entries = when (format) {
                ArchiveFormat.ZIP -> readZipArchive(file)
                ArchiveFormat.RAR -> readRarArchive(file)
                ArchiveFormat.SEVEN_ZIP -> read7ZipArchive(file)
                ArchiveFormat.TAR -> readTarArchive(file, false)
                ArchiveFormat.TAR_GZ -> readTarArchive(file, true)
                ArchiveFormat.UNKNOWN -> throw UnsupportedOperationException("Unsupported archive format")
            }
            
            Result.success(buildFileTree(entries))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun readZipArchive(file: File): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()
        
        ZipArchiveInputStream(BufferedInputStream(FileInputStream(file))).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (!entry.name.startsWith("__MACOSX/") && !entry.name.startsWith(".DS_Store")) {
                    entries.add(
                        ArchiveEntry(
                            name = entry.name.substringAfterLast('/'),
                            path = entry.name,
                            isDirectory = entry.isDirectory,
                            size = entry.size.takeIf { it >= 0 } ?: 0L,
                            compressedSize = 0L, // Compressed size not easily available for ZIP entries
                            lastModified = entry.lastModifiedDate
                        )
                    )
                }
                entry = zipStream.nextEntry
            }
        }
        
        return entries
    }
    
    private fun readRarArchive(file: File): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()
        
        Archive(file).use { archive ->
            for (fileHeader: FileHeader in archive.fileHeaders) {
                if (!fileHeader.fileName.startsWith("__MACOSX/") && !fileHeader.fileName.startsWith(".DS_Store")) {
                    entries.add(
                        ArchiveEntry(
                            name = fileHeader.fileName.substringAfterLast('/'),
                            path = fileHeader.fileName,
                            isDirectory = fileHeader.isDirectory,
                            size = fileHeader.fullUnpackSize,
                            compressedSize = fileHeader.fullPackSize,
                            lastModified = fileHeader.mTime
                        )
                    )
                }
            }
        }
        
        return entries
    }
    
    private fun read7ZipArchive(file: File): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()
        
        SevenZFile(file).use { sevenZFile ->
            var entry = sevenZFile.nextEntry
            while (entry != null) {
                if (!entry.name.startsWith("__MACOSX/") && !entry.name.startsWith(".DS_Store")) {
                    entries.add(
                        ArchiveEntry(
                            name = entry.name.substringAfterLast('/'),
                            path = entry.name,
                            isDirectory = entry.isDirectory,
                            size = entry.size,
                            compressedSize = 0L, // 7z doesn't provide individual compressed sizes easily
                            lastModified = entry.lastModifiedDate
                        )
                    )
                }
                entry = sevenZFile.nextEntry
            }
        }
        
        return entries
    }
    
    private fun readTarArchive(file: File, isGzipped: Boolean): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()
        
        val inputStream = if (isGzipped) {
            GzipCompressorInputStream(BufferedInputStream(FileInputStream(file)))
        } else {
            BufferedInputStream(FileInputStream(file))
        }
        
        TarArchiveInputStream(inputStream).use { tarStream ->
            var entry = tarStream.nextEntry
            while (entry != null) {
                if (!entry.name.startsWith("__MACOSX/") && !entry.name.startsWith(".DS_Store")) {
                    entries.add(
                        ArchiveEntry(
                            name = entry.name.substringAfterLast('/'),
                            path = entry.name,
                            isDirectory = entry.isDirectory,
                            size = entry.size,
                            compressedSize = entry.size, // TAR doesn't compress individual files
                            lastModified = entry.lastModifiedDate
                        )
                    )
                }
                entry = tarStream.nextEntry
            }
        }
        
        return entries
    }
    
    private fun buildFileTree(flatEntries: List<ArchiveEntry>): List<ArchiveEntry> {
        val rootEntries = mutableListOf<ArchiveEntry>()
        val pathToEntry = mutableMapOf<String, ArchiveEntry>()
        
        // First pass: create all entries and index them by path
        for (entry in flatEntries) {
            pathToEntry[entry.path] = entry
        }
        
        // Second pass: build the tree structure
        for (entry in flatEntries) {
            val pathParts = entry.path.split('/')
            
            if (pathParts.size == 1 || (pathParts.size == 2 && pathParts[1].isEmpty() && entry.isDirectory)) {
                // Root level entry
                rootEntries.add(entry)
            } else {
                // Find parent directory
                val parentPath = pathParts.dropLast(1).joinToString("/")
                val parent = pathToEntry[parentPath] ?: pathToEntry["$parentPath/"]
                
                if (parent != null) {
                    parent.children.add(entry)
                } else {
                    // Create missing parent directories
                    createMissingDirectories(pathParts.dropLast(1), pathToEntry, rootEntries, entry)
                }
            }
        }
        
        return rootEntries.sortedWith(compareBy<ArchiveEntry> { !it.isDirectory }.thenBy { it.name })
    }
    
    private fun createMissingDirectories(
        pathParts: List<String>,
        pathToEntry: MutableMap<String, ArchiveEntry>,
        rootEntries: MutableList<ArchiveEntry>,
        childEntry: ArchiveEntry
    ) {
        var currentPath = ""
        var currentParent: ArchiveEntry? = null
        
        for (part in pathParts) {
            currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
            
            var dirEntry = pathToEntry[currentPath] ?: pathToEntry["$currentPath/"]
            
            if (dirEntry == null) {
                // Create missing directory
                dirEntry = ArchiveEntry(
                    name = part,
                    path = currentPath,
                    isDirectory = true,
                    size = 0L,
                    compressedSize = 0L,
                    lastModified = null
                )
                pathToEntry[currentPath] = dirEntry
                
                if (currentParent == null) {
                    rootEntries.add(dirEntry)
                } else {
                    currentParent.children.add(dirEntry)
                }
            }
            
            currentParent = dirEntry
        }
        
        // Add the original child to its parent
        currentParent?.children?.add(childEntry)
    }
}