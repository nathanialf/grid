package com.grid.app.presentation.fileviewer.ebook

import android.util.Log
import org.jsoup.Jsoup
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

data class EpubChapter(
    val id: String,
    val title: String,
    val content: String, // HTML content
    val spineIndex: Int
)

data class EpubBookInfo(
    val title: String,
    val author: String?,
    val chapters: List<EpubChapter>,
    val totalChapters: Int,
    val imagePaths: Map<String, ByteArray> = emptyMap() // Image filename to data mapping
)

class EpubParser {
    
    companion object {
        private const val TAG = "EpubParser"
    }
    
    fun parseEpub(file: File): Result<EpubBookInfo> {
        Log.d(TAG, "Starting EPUB parsing for file: ${file.name} (${file.length()} bytes)")
        return try {
            ZipFile(file).use { zipFile ->
                // Read container.xml to find the OPF file
                Log.d(TAG, "Checking for container.xml...")
                val containerEntry = zipFile.getEntry("META-INF/container.xml")
                if (containerEntry == null) {
                    Log.e(TAG, "container.xml not found in EPUB")
                    return Result.failure(Exception("Invalid EPUB: Missing container.xml"))
                }
                
                Log.d(TAG, "Found container.xml, parsing...")
                val containerDoc = parseXml(zipFile.getInputStream(containerEntry))
                val opfPath = containerDoc.getElementsByTagName("rootfile")
                    .item(0)?.attributes?.getNamedItem("full-path")?.nodeValue
                if (opfPath == null) {
                    Log.e(TAG, "OPF path not found in container.xml")
                    return Result.failure(Exception("Invalid EPUB: Missing OPF path"))
                }
                
                Log.d(TAG, "Found OPF path: $opfPath")
                
                // Read the OPF file
                Log.d(TAG, "Reading OPF file from: $opfPath")
                val opfEntry = zipFile.getEntry(opfPath)
                if (opfEntry == null) {
                    Log.e(TAG, "OPF file not found at path: $opfPath")
                    return Result.failure(Exception("Invalid EPUB: Missing OPF file"))
                }
                
                val opfDoc = parseXml(zipFile.getInputStream(opfEntry))
                val opfBasePath = if (opfPath.contains("/")) {
                    opfPath.substringBeforeLast('/')
                } else {
                    ""
                }
                Log.d(TAG, "OPF base path: '$opfBasePath'")
                
                // Extract metadata
                Log.d(TAG, "Extracting metadata...")
                val metadata = opfDoc.getElementsByTagName("metadata").item(0) as Element
                val title = getElementText(metadata, "dc:title") ?: file.nameWithoutExtension
                val author = getElementText(metadata, "dc:creator")
                Log.d(TAG, "Book metadata - Title: '$title', Author: '$author'")
                
                // Get spine order
                Log.d(TAG, "Processing spine and manifest...")
                val spine = opfDoc.getElementsByTagName("spine").item(0) as Element
                val spineItems = spine.getElementsByTagName("itemref")
                Log.d(TAG, "Found ${spineItems.length} spine items")
                
                // Get manifest items
                val manifest = opfDoc.getElementsByTagName("manifest").item(0) as Element
                val manifestItems = manifest.getElementsByTagName("item")
                Log.d(TAG, "Found ${manifestItems.length} manifest items")
                
                // Build chapter list
                val chapters = mutableListOf<EpubChapter>()
                Log.d(TAG, "Building chapter list...")
                
                for (i in 0 until spineItems.length) {
                    val itemref = spineItems.item(i) as Element
                    val idref = itemref.getAttribute("idref")
                    Log.d(TAG, "Processing spine item $i: idref=$idref")
                    
                    // Find corresponding manifest item
                    var href: String? = null
                    for (j in 0 until manifestItems.length) {
                        val item = manifestItems.item(j) as Element
                        if (item.getAttribute("id") == idref) {
                            href = item.getAttribute("href")
                            break
                        }
                    }
                    
                    if (href != null) {
                        val chapterPath = if (opfBasePath.isNotEmpty()) "$opfBasePath/$href" else href
                        Log.d(TAG, "Chapter $i path: $chapterPath")
                        val chapterEntry = zipFile.getEntry(chapterPath)
                        
                        if (chapterEntry != null) {
                            val content = zipFile.getInputStream(chapterEntry).use { 
                                it.bufferedReader().readText() 
                            }
                            Log.d(TAG, "Chapter $i content length: ${content.length} characters")
                            
                            // Extract chapter title
                            val doc = Jsoup.parse(content)
                            val chapterTitle = doc.select("h1, h2, h3, h4, h5, h6").first()?.text()
                                ?: doc.title().takeIf { it.isNotEmpty() }
                                ?: "Chapter ${i + 1}"
                            
                            Log.d(TAG, "Chapter $i title: '$chapterTitle'")
                            
                            // Log first 200 characters of content for debugging
                            val contentPreview = content.take(200).replace('\n', ' ')
                            Log.d(TAG, "Chapter $i content preview: $contentPreview")
                            
                            chapters.add(
                                EpubChapter(
                                    id = idref,
                                    title = chapterTitle,
                                    content = content,
                                    spineIndex = i
                                )
                            )
                        } else {
                            Log.w(TAG, "Chapter entry not found for path: $chapterPath")
                        }
                    } else {
                        Log.w(TAG, "No href found for spine item $i with idref: $idref")
                    }
                }
                
                Log.d(TAG, "Successfully parsed ${chapters.size} chapters")
                
                // Extract images from the EPUB
                Log.d(TAG, "Extracting images...")
                val imagePaths = mutableMapOf<String, ByteArray>()
                for (i in 0 until manifestItems.length) {
                    val item = manifestItems.item(i) as Element
                    val mediaType = item.getAttribute("media-type")
                    
                    if (mediaType.startsWith("image/")) {
                        val href = item.getAttribute("href")
                        val imagePath = if (opfBasePath.isNotEmpty()) "$opfBasePath/$href" else href
                        val imageEntry = zipFile.getEntry(imagePath)
                        
                        if (imageEntry != null) {
                            val imageData = zipFile.getInputStream(imageEntry).use { 
                                it.readBytes() 
                            }
                            // Store with just the filename as key for easier reference
                            val filename = href.substringAfterLast('/')
                            imagePaths[filename] = imageData
                            // Also store with full path in case content references it that way
                            imagePaths[href] = imageData
                        }
                    }
                }
                
                Log.d(TAG, "Extracted ${imagePaths.size} images")
                Log.d(TAG, "EPUB parsing completed successfully - Title: '$title', Chapters: ${chapters.size}, Images: ${imagePaths.size}")
                
                Result.success(
                    EpubBookInfo(
                        title = title,
                        author = author,
                        chapters = chapters,
                        totalChapters = chapters.size,
                        imagePaths = imagePaths
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "EPUB parsing failed for file: ${file.name}", e)
            Result.failure(e)
        }
    }
    
    private fun parseXml(inputStream: java.io.InputStream): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        return builder.parse(inputStream)
    }
    
    private fun getElementText(parent: Element, tagName: String): String? {
        val elements = parent.getElementsByTagName(tagName)
        return if (elements.length > 0) {
            elements.item(0).textContent?.trim()
        } else null
    }
}