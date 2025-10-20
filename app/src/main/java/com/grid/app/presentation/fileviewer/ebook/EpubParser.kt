package com.grid.app.presentation.fileviewer.ebook

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
    
    fun parseEpub(file: File): Result<EpubBookInfo> {
        return try {
            ZipFile(file).use { zipFile ->
                // Read container.xml to find the OPF file
                val containerEntry = zipFile.getEntry("META-INF/container.xml")
                    ?: return Result.failure(Exception("Invalid EPUB: Missing container.xml"))
                
                val containerDoc = parseXml(zipFile.getInputStream(containerEntry))
                val opfPath = containerDoc.getElementsByTagName("rootfile")
                    .item(0)?.attributes?.getNamedItem("full-path")?.nodeValue
                    ?: return Result.failure(Exception("Invalid EPUB: Missing OPF path"))
                
                // Read the OPF file
                val opfEntry = zipFile.getEntry(opfPath)
                    ?: return Result.failure(Exception("Invalid EPUB: Missing OPF file"))
                
                val opfDoc = parseXml(zipFile.getInputStream(opfEntry))
                val opfBasePath = opfPath.substringBeforeLast('/')
                
                // Extract metadata
                val metadata = opfDoc.getElementsByTagName("metadata").item(0) as Element
                val title = getElementText(metadata, "dc:title") ?: file.nameWithoutExtension
                val author = getElementText(metadata, "dc:creator")
                
                // Get spine order
                val spine = opfDoc.getElementsByTagName("spine").item(0) as Element
                val spineItems = spine.getElementsByTagName("itemref")
                
                // Get manifest items
                val manifest = opfDoc.getElementsByTagName("manifest").item(0) as Element
                val manifestItems = manifest.getElementsByTagName("item")
                
                // Build chapter list
                val chapters = mutableListOf<EpubChapter>()
                
                for (i in 0 until spineItems.length) {
                    val itemref = spineItems.item(i) as Element
                    val idref = itemref.getAttribute("idref")
                    
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
                        val chapterEntry = zipFile.getEntry(chapterPath)
                        
                        if (chapterEntry != null) {
                            val content = zipFile.getInputStream(chapterEntry).use { 
                                it.bufferedReader().readText() 
                            }
                            
                            // Extract chapter title
                            val doc = Jsoup.parse(content)
                            val chapterTitle = doc.select("h1, h2, h3, h4, h5, h6").first()?.text()
                                ?: doc.title().takeIf { it.isNotEmpty() }
                                ?: "Chapter ${i + 1}"
                            
                            chapters.add(
                                EpubChapter(
                                    id = idref,
                                    title = chapterTitle,
                                    content = content,
                                    spineIndex = i
                                )
                            )
                        }
                    }
                }
                
                // Extract images from the EPUB
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