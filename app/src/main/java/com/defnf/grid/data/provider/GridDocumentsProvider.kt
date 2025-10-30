package com.defnf.grid.data.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.defnf.grid.R
import com.defnf.grid.domain.model.Connection
import com.defnf.grid.domain.model.RemoteFile
import com.defnf.grid.domain.repository.ConnectionRepository
import com.defnf.grid.domain.repository.FileRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.FileNotFoundException

class GridDocumentsProvider : DocumentsProvider() {
    
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface GridDocumentsProviderEntryPoint {
        fun connectionRepository(): ConnectionRepository
        fun fileRepository(): FileRepository
    }
    
    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(
            context!!.applicationContext,
            GridDocumentsProviderEntryPoint::class.java
        )
    }
    
    private val connectionRepository by lazy { entryPoint.connectionRepository() }
    private val fileRepository by lazy { entryPoint.fileRepository() }
    
    companion object {
        private const val AUTHORITY = "com.defnf.grid.documents"
        private const val ROOT_CONNECTION_LIST = "connections"
        
        fun notifyRootsChanged(context: android.content.Context) {
            try {
                val uri = DocumentsContract.buildRootsUri(AUTHORITY)
                context.contentResolver.notifyChange(uri, null)
                android.util.Log.d("GridDocumentsProvider", "Notified system of roots change")
            } catch (e: Exception) {
                android.util.Log.e("GridDocumentsProvider", "Failed to notify roots change", e)
            }
        }
        
        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
        )
        
        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        )
    }
    
    override fun onCreate(): Boolean {
        android.util.Log.d("GridDocumentsProvider", "onCreate() called - provider is initializing")
        return true
    }
    
    override fun queryRoots(projection: Array<String>?): Cursor {
        android.util.Log.d("GridDocumentsProvider", "queryRoots() called - system is requesting available roots")
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        
        try {
            runBlocking {
                // Add timeout to prevent hanging the system picker
                withTimeoutOrNull(5000) {
                    try {
                        val connections = connectionRepository.getAllConnections().first()
                        android.util.Log.d("GridDocumentsProvider", "Found ${connections.size} connections")
                        // Only show connections if we have any configured
                        if (connections.isNotEmpty()) {
                            for (connection in connections) {
                                val row = result.newRow()
                                row.add(DocumentsContract.Root.COLUMN_ROOT_ID, connection.id)
                                row.add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*")
                                row.add(DocumentsContract.Root.COLUMN_FLAGS, 
                                    DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD or
                                    DocumentsContract.Root.FLAG_LOCAL_ONLY)
                                row.add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher)
                                row.add(DocumentsContract.Root.COLUMN_TITLE, "Grid: ${connection.name}")
                                row.add(DocumentsContract.Root.COLUMN_SUMMARY, "${connection.protocol.name} - ${connection.hostname}")
                                row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, "${connection.id}:/")
                                row.add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, -1)
                            }
                        }
                    } catch (e: Exception) {
                        // Log error but don't crash the picker
                        android.util.Log.e("GridDocumentsProvider", "Error loading connections", e)
                    }
                    Unit
                } ?: android.util.Log.w("GridDocumentsProvider", "Timeout loading connections")
            }
        } catch (e: Exception) {
            // Ensure we never crash the system picker
            android.util.Log.e("GridDocumentsProvider", "Error in queryRoots", e)
        }
        
        return result
    }
    
    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        
        try {
            runBlocking {
                try {
                    val (connectionId, path) = parseDocumentId(documentId)
                    val connection = connectionRepository.getConnectionById(connectionId)
                    
                    if (path == "/") {
                        // Root directory
                        val row = result.newRow()
                        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
                        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
                        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, connection.name)
                        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, null)
                        row.add(DocumentsContract.Document.COLUMN_FLAGS, 0)
                        row.add(DocumentsContract.Document.COLUMN_SIZE, null)
                    } else {
                        // Get file info
                        val files = fileRepository.getFiles(connection, path.substringBeforeLast('/'))
                        val fileName = path.substringAfterLast('/')
                        val file = files.find { it.name == fileName }
                        
                        file?.let {
                            addFileToResult(result, connection, it, path)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("GridDocumentsProvider", "Error in queryDocument", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GridDocumentsProvider", "Error in queryDocument outer", e)
        }
        
        return result
    }
    
    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String?
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        
        try {
            runBlocking {
                try {
                    val (connectionId, path) = parseDocumentId(parentDocumentId)
                    val connection = connectionRepository.getConnectionById(connectionId)
                    val files = fileRepository.getFiles(connection, path)
                    
                    for (file in files) {
                        val childPath = if (path == "/") "/${file.name}" else "$path/${file.name}"
                        addFileToResult(result, connection, file, childPath)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("GridDocumentsProvider", "Error in queryChildDocuments", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GridDocumentsProvider", "Error in queryChildDocuments outer", e)
        }
        
        return result
    }
    
    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        try {
            return runBlocking {
                try {
                    val (connectionId, path) = parseDocumentId(documentId)
                    val connection = connectionRepository.getConnectionById(connectionId)
                    val fileName = path.substringAfterLast('/')
                    val parentPath = if (path.contains('/')) path.substringBeforeLast('/') else "/"
                    val files = fileRepository.getFiles(connection, parentPath)
                    val file = files.find { it.name == fileName } ?: throw FileNotFoundException("File not found: $path")
                    
                    // Download file to temporary location and return descriptor
                    val tempFile = fileRepository.downloadFileToTemp(connection, file)
                    ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                } catch (e: Exception) {
                    android.util.Log.e("GridDocumentsProvider", "Error in openDocument", e)
                    throw FileNotFoundException("Cannot open document: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GridDocumentsProvider", "Error in openDocument outer", e)
            throw FileNotFoundException("Cannot open document: ${e.message}")
        }
    }
    
    private fun parseDocumentId(documentId: String): Pair<String, String> {
        val parts = documentId.split(":", limit = 2)
        return parts[0] to (parts.getOrNull(1) ?: "/")
    }
    
    private fun addFileToResult(
        result: MatrixCursor,
        connection: Connection,
        file: RemoteFile,
        path: String
    ) {
        try {
            val documentId = "${connection.id}:$path"
            val mimeType = if (file.isDirectory) {
                DocumentsContract.Document.MIME_TYPE_DIR
            } else {
                getMimeType(file.name)
            }
            
            val row = result.newRow()
            row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
            row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType)
            row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.name)
            row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified)
            row.add(DocumentsContract.Document.COLUMN_FLAGS, 0) // Remove all flags for safety
            row.add(DocumentsContract.Document.COLUMN_SIZE, if (file.isDirectory) null else file.size)
        } catch (e: Exception) {
            android.util.Log.e("GridDocumentsProvider", "Error adding file to result: ${file.name}", e)
        }
    }
    
    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "application/octet-stream"
    }
}