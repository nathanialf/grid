package com.defnf.grid.presentation.fileviewer.composables.archive

import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.*
import kotlin.math.max

data class ExtractionProgress(
    val currentFile: String,
    val filesProcessed: Int,
    val totalFiles: Int,
    val bytesProcessed: Long,
    val totalBytes: Long,
    val isComplete: Boolean = false,
    val error: String? = null
) {
    val fileProgress: Float
        get() = if (totalFiles > 0) filesProcessed.toFloat() / totalFiles.toFloat() else 0f
        
    val bytesProgress: Float
        get() = if (totalBytes > 0) bytesProcessed.toFloat() / totalBytes.toFloat() else 0f
}

class ArchiveExtractor {
    
    fun extractArchive(
        archiveFile: File,
        outputDirectory: File
    ): Flow<ExtractionProgress> = flow {
        try {
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs()
            }
            
            val format = ArchiveFormat.fromFileName(archiveFile.name)
            
            when (format) {
                ArchiveFormat.ZIP -> extractZip(archiveFile, outputDirectory)
                ArchiveFormat.RAR -> extractRar(archiveFile, outputDirectory)
                ArchiveFormat.SEVEN_ZIP -> extract7Zip(archiveFile, outputDirectory)
                ArchiveFormat.TAR -> extractTar(archiveFile, outputDirectory, false)
                ArchiveFormat.TAR_GZ -> extractTar(archiveFile, outputDirectory, true)
                ArchiveFormat.UNKNOWN -> throw UnsupportedOperationException("Unsupported archive format")
            }.collect { progress ->
                emit(progress)
            }
            
            emit(ExtractionProgress(
                currentFile = "",
                filesProcessed = 0,
                totalFiles = 0,
                bytesProcessed = 0L,
                totalBytes = 0L,
                isComplete = true
            ))
            
        } catch (e: Exception) {
            emit(ExtractionProgress(
                currentFile = "",
                filesProcessed = 0,
                totalFiles = 0,
                bytesProcessed = 0L,
                totalBytes = 0L,
                isComplete = false,
                error = e.message
            ))
        }
    }.flowOn(Dispatchers.IO)
    
    private fun extractZip(
        archiveFile: File,
        outputDirectory: File
    ): Flow<ExtractionProgress> = flow {
        // First pass: count entries and total size
        var totalFiles = 0
        var totalBytes = 0L
        
        ZipArchiveInputStream(BufferedInputStream(FileInputStream(archiveFile))).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (!entry.name.startsWith("__MACOSX/") && !entry.name.startsWith(".DS_Store")) {
                    totalFiles++
                    totalBytes += max(entry.size, 0L)
                }
                entry = zipStream.nextEntry
            }
        }
        
        // Second pass: extract files
        var filesProcessed = 0
        var bytesProcessed = 0L
        
        ZipArchiveInputStream(BufferedInputStream(FileInputStream(archiveFile))).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (!entry.name.startsWith("__MACOSX/") && !entry.name.startsWith(".DS_Store")) {
                    val outputFile = File(outputDirectory, entry.name)
                    
                    emit(ExtractionProgress(
                        currentFile = entry.name,
                        filesProcessed = filesProcessed,
                        totalFiles = totalFiles,
                        bytesProcessed = bytesProcessed,
                        totalBytes = totalBytes
                    ))
                    
                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile?.mkdirs()
                        FileOutputStream(outputFile).use { output ->
                            zipStream.copyTo(output)
                        }
                        
                        // Set last modified time if available
                        entry.lastModifiedDate?.let { outputFile.setLastModified(it.time) }
                    }
                    
                    filesProcessed++
                    bytesProcessed += max(entry.size, 0L)
                }
                entry = zipStream.nextEntry
            }
        }
    }
    
    private fun extractRar(
        archiveFile: File,
        outputDirectory: File
    ): Flow<ExtractionProgress> = flow {
        Archive(archiveFile).use { archive ->
            val fileHeaders = archive.fileHeaders.filter { 
                !it.fileName.startsWith("__MACOSX/") && !it.fileName.startsWith(".DS_Store") 
            }
            
            val totalFiles = fileHeaders.size
            val totalBytes = fileHeaders.sumOf { it.fullUnpackSize }
            
            var filesProcessed = 0
            var bytesProcessed = 0L
            
            for (fileHeader in fileHeaders) {
                emit(ExtractionProgress(
                    currentFile = fileHeader.fileName,
                    filesProcessed = filesProcessed,
                    totalFiles = totalFiles,
                    bytesProcessed = bytesProcessed,
                    totalBytes = totalBytes
                ))
                
                val outputFile = File(outputDirectory, fileHeader.fileName)
                
                if (fileHeader.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    FileOutputStream(outputFile).use { output ->
                        archive.extractFile(fileHeader, output)
                    }
                    
                    // Set last modified time if available
                    fileHeader.mTime?.let { outputFile.setLastModified(it.time) }
                }
                
                filesProcessed++
                bytesProcessed += fileHeader.fullUnpackSize
            }
        }
    }
    
    private fun extract7Zip(
        archiveFile: File,
        outputDirectory: File
    ): Flow<ExtractionProgress> = flow {
        SevenZFile(archiveFile).use { sevenZFile ->
            // Count entries
            val entries = mutableListOf<org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry>()
            var entry = sevenZFile.nextEntry
            while (entry != null) {
                if (!entry.name.startsWith("__MACOSX/") && !entry.name.startsWith(".DS_Store")) {
                    entries.add(entry)
                }
                entry = sevenZFile.nextEntry
            }
            
            val totalFiles = entries.size
            val totalBytes = entries.sumOf { it.size }
            
            // Reset and extract
            sevenZFile.close()
            SevenZFile(archiveFile).use { extractSevenZFile ->
                var filesProcessed = 0
                var bytesProcessed = 0L
                
                var extractEntry = extractSevenZFile.nextEntry
                while (extractEntry != null) {
                    if (!extractEntry.name.startsWith("__MACOSX/") && !extractEntry.name.startsWith(".DS_Store")) {
                        emit(ExtractionProgress(
                            currentFile = extractEntry.name,
                            filesProcessed = filesProcessed,
                            totalFiles = totalFiles,
                            bytesProcessed = bytesProcessed,
                            totalBytes = totalBytes
                        ))
                        
                        val outputFile = File(outputDirectory, extractEntry.name)
                        
                        if (extractEntry.isDirectory) {
                            outputFile.mkdirs()
                        } else {
                            outputFile.parentFile?.mkdirs()
                            FileOutputStream(outputFile).use { output ->
                                val buffer = ByteArray(8192)
                                var read: Int
                                while (extractSevenZFile.read(buffer).also { read = it } > 0) {
                                    output.write(buffer, 0, read)
                                }
                            }
                            
                            // Set last modified time if available
                            extractEntry.lastModifiedDate?.let { outputFile.setLastModified(it.time) }
                        }
                        
                        filesProcessed++
                        bytesProcessed += extractEntry.size
                    }
                    extractEntry = extractSevenZFile.nextEntry
                }
            }
        }
    }
    
    private fun extractTar(
        archiveFile: File,
        outputDirectory: File,
        isGzipped: Boolean
    ): Flow<ExtractionProgress> = flow {
        val inputStream = if (isGzipped) {
            GzipCompressorInputStream(BufferedInputStream(FileInputStream(archiveFile)))
        } else {
            BufferedInputStream(FileInputStream(archiveFile))
        }
        
        // First pass: count entries
        var totalFiles = 0
        var totalBytes = 0L
        
        TarArchiveInputStream(inputStream).use { tarStream ->
            var entry = tarStream.nextEntry
            while (entry != null) {
                if (!entry.name.startsWith("__MACOSX/") && !entry.name.startsWith(".DS_Store")) {
                    totalFiles++
                    totalBytes += entry.size
                }
                entry = tarStream.nextEntry
            }
        }
        
        // Second pass: extract
        val extractInputStream = if (isGzipped) {
            GzipCompressorInputStream(BufferedInputStream(FileInputStream(archiveFile)))
        } else {
            BufferedInputStream(FileInputStream(archiveFile))
        }
        
        TarArchiveInputStream(extractInputStream).use { tarStream ->
            var filesProcessed = 0
            var bytesProcessed = 0L
            
            var entry = tarStream.nextEntry
            while (entry != null) {
                if (!entry.name.startsWith("__MACOSX/") && !entry.name.startsWith(".DS_Store")) {
                    emit(ExtractionProgress(
                        currentFile = entry.name,
                        filesProcessed = filesProcessed,
                        totalFiles = totalFiles,
                        bytesProcessed = bytesProcessed,
                        totalBytes = totalBytes
                    ))
                    
                    val outputFile = File(outputDirectory, entry.name)
                    
                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile?.mkdirs()
                        FileOutputStream(outputFile).use { output ->
                            tarStream.copyTo(output)
                        }
                        
                        // Set last modified time if available
                        entry.lastModifiedDate?.let { outputFile.setLastModified(it.time) }
                    }
                    
                    filesProcessed++
                    bytesProcessed += entry.size
                }
                entry = tarStream.nextEntry
            }
        }
    }
}