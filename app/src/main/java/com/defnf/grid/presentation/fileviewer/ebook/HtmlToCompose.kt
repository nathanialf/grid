package com.defnf.grid.presentation.fileviewer.ebook

import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

data class ParsedContent(
    val text: AnnotatedString,
    val images: List<ImageInfo> = emptyList()
)

data class ImageInfo(
    val src: String,
    val alt: String? = null,
    val positionInText: Int
)

object HtmlToCompose {
    
    private const val TAG = "HtmlToCompose"
    
    @Composable
    fun parseHtmlToAnnotatedString(html: String): AnnotatedString {
        return parseHtmlToContent(html).text
    }
    
    @Composable
    fun parseHtmlToContent(html: String): ParsedContent {
        Log.d(TAG, "Starting HTML parsing - content length: ${html.length}")
        
        // Handle empty or whitespace-only HTML
        if (html.isBlank()) {
            Log.w(TAG, "HTML content is blank or empty")
            return ParsedContent(AnnotatedString("No content available"), emptyList())
        }
        
        val doc = Jsoup.parse(html)
        val body = doc.body() ?: doc
        val images = mutableListOf<ImageInfo>()
        
        Log.d(TAG, "Parsed HTML document - body element: ${body.tagName()}, child count: ${body.childNodeSize()}")
        
        // Log a preview of the HTML structure
        val htmlPreview = html.take(300).replace('\n', ' ')
        Log.d(TAG, "HTML preview: $htmlPreview")
        
        val text = buildAnnotatedString {
            parseElement(body, this, images)
        }
        
        Log.d(TAG, "Extracted text length: ${text.text.length}, images: ${images.size}")
        
        // If no text was extracted, fall back to plain text extraction
        if (text.text.isBlank()) {
            Log.w(TAG, "No styled text extracted, falling back to plain text")
            val plainText = body.text()
            Log.d(TAG, "Plain text length: ${plainText.length}")
            if (plainText.isNotBlank()) {
                Log.d(TAG, "Using plain text fallback")
                return ParsedContent(AnnotatedString(plainText), images)
            } else {
                Log.w(TAG, "Plain text also empty, using HTML stripping fallback")
                // Last resort: return raw HTML without tags
                val strippedText = html.replace(Regex("<[^>]*>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                Log.d(TAG, "Stripped text length: ${strippedText.length}")
                return ParsedContent(
                    AnnotatedString(strippedText.ifBlank { "No readable content found" }),
                    images
                )
            }
        }
        
        Log.d(TAG, "HTML parsing completed successfully")
        return ParsedContent(text, images)
    }
    
    private fun parseElement(element: Element, builder: AnnotatedString.Builder, images: MutableList<ImageInfo>) {
        // Check for inline CSS styles
        val style = element.attr("style")
        val cssStyle = if (style.isNotEmpty()) parseCssStyle(style) else null
        for (child in element.childNodes()) {
            when (child) {
                is TextNode -> {
                    val text = child.text()
                    if (text.isNotBlank()) {
                        builder.append(text)
                    }
                }
                is Element -> {
                    when (child.tagName().lowercase()) {
                        "h1", "h2", "h3", "h4", "h5", "h6" -> {
                            val fontSize = when (child.tagName().lowercase()) {
                                "h1" -> 28.sp
                                "h2" -> 24.sp
                                "h3" -> 20.sp
                                "h4" -> 18.sp
                                "h5" -> 16.sp
                                else -> 14.sp
                            }
                            
                            if (builder.length > 0) builder.append("\n\n")
                            
                            val start = builder.length
                            parseElement(child, builder, images)
                            val end = builder.length
                            
                            builder.addStyle(
                                style = SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = fontSize
                                ),
                                start = start,
                                end = end
                            )
                            
                            builder.append("\n\n")
                        }
                        "p" -> {
                            if (builder.length > 0) builder.append("\n\n")
                            parseElement(child, builder, images)
                            builder.append("\n")
                        }
                        "br" -> {
                            builder.append("\n")
                        }
                        "strong", "b" -> {
                            val start = builder.length
                            parseElement(child, builder, images)
                            val end = builder.length
                            
                            builder.addStyle(
                                style = SpanStyle(fontWeight = FontWeight.Bold),
                                start = start,
                                end = end
                            )
                        }
                        "em", "i" -> {
                            val start = builder.length
                            parseElement(child, builder, images)
                            val end = builder.length
                            
                            builder.addStyle(
                                style = SpanStyle(fontStyle = FontStyle.Italic),
                                start = start,
                                end = end
                            )
                        }
                        "u" -> {
                            val start = builder.length
                            parseElement(child, builder, images)
                            val end = builder.length
                            
                            builder.addStyle(
                                style = SpanStyle(textDecoration = TextDecoration.Underline),
                                start = start,
                                end = end
                            )
                        }
                        "s", "strike", "del" -> {
                            val start = builder.length
                            parseElement(child, builder, images)
                            val end = builder.length
                            
                            builder.addStyle(
                                style = SpanStyle(textDecoration = TextDecoration.LineThrough),
                                start = start,
                                end = end
                            )
                        }
                        "sup" -> {
                            val start = builder.length
                            parseElement(child, builder, images)
                            val end = builder.length
                            
                            builder.addStyle(
                                style = SpanStyle(
                                    baselineShift = BaselineShift.Superscript,
                                    fontSize = 12.sp
                                ),
                                start = start,
                                end = end
                            )
                        }
                        "sub" -> {
                            val start = builder.length
                            parseElement(child, builder, images)
                            val end = builder.length
                            
                            builder.addStyle(
                                style = SpanStyle(
                                    baselineShift = BaselineShift.Subscript,
                                    fontSize = 12.sp
                                ),
                                start = start,
                                end = end
                            )
                        }
                        "code" -> {
                            val start = builder.length
                            parseElement(child, builder, images)
                            val end = builder.length
                            
                            builder.addStyle(
                                style = SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    background = Color.Gray.copy(alpha = 0.1f),
                                    fontSize = 16.sp
                                ),
                                start = start,
                                end = end
                            )
                        }
                        "tt" -> {
                            val start = builder.length
                            parseElement(child, builder, images)
                            val end = builder.length
                            
                            builder.addStyle(
                                style = SpanStyle(fontFamily = FontFamily.Monospace),
                                start = start,
                                end = end
                            )
                        }
                        "small" -> {
                            val start = builder.length
                            parseElement(child, builder, images)
                            val end = builder.length
                            
                            builder.addStyle(
                                style = SpanStyle(fontSize = 14.sp),
                                start = start,
                                end = end
                            )
                        }
                        "big" -> {
                            val start = builder.length
                            parseElement(child, builder, images)
                            val end = builder.length
                            
                            builder.addStyle(
                                style = SpanStyle(fontSize = 22.sp),
                                start = start,
                                end = end
                            )
                        }
                        "mark" -> {
                            val start = builder.length
                            parseElement(child, builder, images)
                            val end = builder.length
                            
                            builder.addStyle(
                                style = SpanStyle(
                                    background = Color.Yellow.copy(alpha = 0.3f)
                                ),
                                start = start,
                                end = end
                            )
                        }
                        "a" -> {
                            val start = builder.length
                            parseElement(child, builder, images)
                            val end = builder.length
                            
                            // Style links with underline and different color
                            builder.addStyle(
                                style = SpanStyle(
                                    color = Color.Blue,
                                    textDecoration = TextDecoration.Underline
                                ),
                                start = start,
                                end = end
                            )
                        }
                        "blockquote" -> {
                            if (builder.length > 0) builder.append("\n\n")
                            
                            val start = builder.length
                            builder.append("    ") // Indent
                            parseElement(child, builder, images)
                            val end = builder.length
                            
                            builder.addStyle(
                                style = SpanStyle(
                                    fontStyle = FontStyle.Italic,
                                    color = Color.Gray
                                ),
                                start = start,
                                end = end
                            )
                            
                            builder.append("\n\n")
                        }
                        "cite" -> {
                            val start = builder.length
                            parseElement(child, builder, images)
                            val end = builder.length
                            
                            builder.addStyle(
                                style = SpanStyle(
                                    fontStyle = FontStyle.Italic,
                                    color = Color.Gray
                                ),
                                start = start,
                                end = end
                            )
                        }
                        "q" -> {
                            builder.append("\"")
                            parseElement(child, builder, images)
                            builder.append("\"")
                        }
                        "pre" -> {
                            if (builder.length > 0) builder.append("\n\n")
                            
                            val start = builder.length
                            parseElement(child, builder, images)
                            val end = builder.length
                            
                            builder.addStyle(
                                style = SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    background = Color.Gray.copy(alpha = 0.1f),
                                    fontSize = 16.sp
                                ),
                                start = start,
                                end = end
                            )
                            
                            builder.append("\n\n")
                        }
                        "div", "span", "section", "article" -> {
                            parseElement(child, builder, images)
                        }
                        "img" -> {
                            val src = child.attr("src")
                            val alt = child.attr("alt")
                            
                            if (src.isNotEmpty()) {
                                val placeholder = if (alt.isNotEmpty()) "[$alt]" else "[Image]"
                                
                                images.add(
                                    ImageInfo(
                                        src = src,
                                        alt = alt.takeIf { it.isNotEmpty() },
                                        positionInText = builder.length
                                    )
                                )
                                
                                // Add placeholder text for the image
                                builder.append(placeholder)
                            }
                        }
                        // Common HTML container and semantic tags that should just pass through content
                        "main", "header", "footer", "nav", "aside", "body", "html", "center", "font" -> {
                            parseElement(child, builder, images)
                        }
                        // List elements
                        "ul", "ol" -> {
                            if (builder.length > 0) builder.append("\n")
                            parseElement(child, builder, images)
                            builder.append("\n")
                        }
                        "li" -> {
                            if (builder.length > 0) builder.append("\n• ")
                            parseElement(child, builder, images)
                        }
                        else -> {
                            // For any other tag, just parse the content
                            parseElement(child, builder, images)
                        }
                    }
                }
            }
        }
        
        // Apply CSS style if present
        if (cssStyle != null && builder.length > 0) {
            // This is a simplified CSS parser - in practice you'd want more robust parsing
            // For now, just handle basic styles like color, font-weight, etc.
        }
    }
    
    // Simple CSS style parser for inline styles
    private fun parseCssStyle(style: String): Map<String, String> {
        val cssMap = mutableMapOf<String, String>()
        style.split(";").forEach { declaration ->
            val parts = declaration.split(":")
            if (parts.size == 2) {
                val property = parts[0].trim().lowercase()
                val value = parts[1].trim()
                cssMap[property] = value
            }
        }
        return cssMap
    }
    
    // Theme-aware color function (would need MaterialTheme context)
    @Composable
    private fun getThemeAwareColor(htmlColor: String): Color {
        return when (htmlColor.lowercase()) {
            "blue" -> MaterialTheme.colorScheme.primary
            "red" -> Color.Red
            "green" -> Color.Green
            "gray", "grey" -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        }
    }
}