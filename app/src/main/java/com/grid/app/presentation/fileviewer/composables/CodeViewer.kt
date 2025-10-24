package com.grid.app.presentation.fileviewer.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun CodeViewer(
    file: File,
    modifier: Modifier = Modifier
) {
    var content by remember { mutableStateOf("Loading...") }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // Detect language from file extension
    val language = remember(file) { detectLanguageFromExtension(file.extension) }
    
    LaunchedEffect(file) {
        try {
            isLoading = true
            error = null
            content = withContext(Dispatchers.IO) {
                file.readText()
            }
        } catch (e: Exception) {
            error = "Error reading file: ${e.message}"
        } finally {
            isLoading = false
        }
    }
    
    when {
        isLoading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        error != null -> {
            Column(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Failed to load code file",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        else -> {
            // Get color scheme outside of remember
            val colorScheme = MaterialTheme.colorScheme
            
            // Code content with syntax highlighting
            val highlightedContent = remember(content, language, colorScheme) {
                applySyntaxHighlighting(content, language, colorScheme)
            }
            
            Column(
                modifier = modifier.fillMaxSize()
            ) {
                // Language indicator
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = language.displayName,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Code content with line numbers
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(rememberScrollState())
                ) {
                    // Line numbers column
                    val lines = content.lines()
                    val maxLineNumber = lines.size
                    val lineNumberWidth = maxLineNumber.toString().length
                    
                    Column(
                        modifier = Modifier
                            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
                            .width(IntrinsicSize.Min)
                    ) {
                        lines.forEachIndexed { index, _ ->
                            Text(
                                text = "${index + 1}".padStart(lineNumberWidth, ' '),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2
                            )
                        }
                    }
                    
                    // Vertical divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .padding(vertical = 16.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    )
                    
                    // Code content
                    Text(
                        text = highlightedContent,
                        modifier = Modifier
                            .padding(start = 12.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
                            .fillMaxWidth(),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2
                    )
                }
            }
        }
    }
}

private enum class ProgrammingLanguage(val displayName: String) {
    KOTLIN("Kotlin"),
    JAVA("Java"),
    JAVASCRIPT("JavaScript"),
    TYPESCRIPT("TypeScript"),
    PYTHON("Python"),
    CPP("C++"),
    C("C"),
    CSHARP("C#"),
    GO("Go"),
    RUST("Rust"),
    PHP("PHP"),
    RUBY("Ruby"),
    SWIFT("Swift"),
    DART("Dart"),
    HTML("HTML"),
    CSS("CSS"),
    XML("XML"),
    JSON("JSON"),
    YAML("YAML"),
    MARKDOWN("Markdown"),
    SHELL("Shell"),
    SQL("SQL"),
    UNKNOWN("Code")
}

private fun detectLanguageFromExtension(extension: String): ProgrammingLanguage {
    return when (extension.lowercase()) {
        "kt", "kts" -> ProgrammingLanguage.KOTLIN
        "java" -> ProgrammingLanguage.JAVA
        "js", "mjs" -> ProgrammingLanguage.JAVASCRIPT
        "ts" -> ProgrammingLanguage.TYPESCRIPT
        "py", "pyw" -> ProgrammingLanguage.PYTHON
        "cpp", "cxx", "cc", "c++" -> ProgrammingLanguage.CPP
        "c", "h" -> ProgrammingLanguage.C
        "cs" -> ProgrammingLanguage.CSHARP
        "go" -> ProgrammingLanguage.GO
        "rs" -> ProgrammingLanguage.RUST
        "php" -> ProgrammingLanguage.PHP
        "rb" -> ProgrammingLanguage.RUBY
        "swift" -> ProgrammingLanguage.SWIFT
        "dart" -> ProgrammingLanguage.DART
        "html", "htm" -> ProgrammingLanguage.HTML
        "css" -> ProgrammingLanguage.CSS
        "xml" -> ProgrammingLanguage.XML
        "json" -> ProgrammingLanguage.JSON
        "yml", "yaml" -> ProgrammingLanguage.YAML
        "md" -> ProgrammingLanguage.MARKDOWN
        "sh", "bash", "zsh" -> ProgrammingLanguage.SHELL
        "sql" -> ProgrammingLanguage.SQL
        else -> ProgrammingLanguage.UNKNOWN
    }
}

private fun applySyntaxHighlighting(code: String, language: ProgrammingLanguage, colorScheme: androidx.compose.material3.ColorScheme): AnnotatedString {
    
    // Define colors for syntax highlighting
    val keywordColor = colorScheme.primary
    val stringColor = colorScheme.secondary
    val commentColor = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val numberColor = colorScheme.tertiary
    val operatorColor = colorScheme.onSurface.copy(alpha = 0.8f)
    
    return buildAnnotatedString {
        val lines = code.lines()
        
        lines.forEachIndexed { lineIndex, line ->
            val highlightedLine = highlightLine(
                line = line,
                language = language,
                keywordColor = keywordColor,
                stringColor = stringColor,
                commentColor = commentColor,
                numberColor = numberColor,
                operatorColor = operatorColor
            )
            
            append(highlightedLine)
            if (lineIndex < lines.size - 1) {
                append("\n")
            }
        }
    }
}

private fun highlightLine(
    line: String,
    language: ProgrammingLanguage,
    keywordColor: Color,
    stringColor: Color,
    commentColor: Color,
    numberColor: Color,
    operatorColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        
        while (i < line.length) {
            when {
                // Comments
                line.substring(i).startsWith("//") || 
                line.substring(i).startsWith("#") ||
                (line.substring(i).startsWith("/*") && language != ProgrammingLanguage.CSS) -> {
                    val commentEnd = if (line.substring(i).startsWith("/*")) {
                        val endIndex = line.indexOf("*/", i + 2)
                        if (endIndex != -1) endIndex + 2 else line.length
                    } else line.length
                    
                    withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) {
                        append(line.substring(i, commentEnd))
                    }
                    i = commentEnd
                }
                
                // Strings
                line[i] == '"' || line[i] == '\'' -> {
                    val quote = line[i]
                    var stringEnd = i + 1
                    while (stringEnd < line.length && line[stringEnd] != quote) {
                        if (line[stringEnd] == '\\') stringEnd++ // Skip escaped characters
                        stringEnd++
                    }
                    if (stringEnd < line.length) stringEnd++ // Include closing quote
                    
                    withStyle(SpanStyle(color = stringColor)) {
                        append(line.substring(i, stringEnd))
                    }
                    i = stringEnd
                }
                
                // Numbers
                line[i].isDigit() -> {
                    var numberEnd = i
                    while (numberEnd < line.length && (line[numberEnd].isDigit() || line[numberEnd] == '.')) {
                        numberEnd++
                    }
                    
                    withStyle(SpanStyle(color = numberColor)) {
                        append(line.substring(i, numberEnd))
                    }
                    i = numberEnd
                }
                
                // Keywords and identifiers
                line[i].isLetter() || line[i] == '_' -> {
                    var wordEnd = i
                    while (wordEnd < line.length && (line[wordEnd].isLetterOrDigit() || line[wordEnd] == '_')) {
                        wordEnd++
                    }
                    
                    val word = line.substring(i, wordEnd)
                    if (isKeyword(word, language)) {
                        withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold)) {
                            append(word)
                        }
                    } else {
                        append(word)
                    }
                    i = wordEnd
                }
                
                // Operators
                "{}()[]<>=+->*/%!&|".contains(line[i]) -> {
                    withStyle(SpanStyle(color = operatorColor)) {
                        append(line[i])
                    }
                    i++
                }
                
                // Regular characters
                else -> {
                    append(line[i])
                    i++
                }
            }
        }
    }
}

private fun isKeyword(word: String, language: ProgrammingLanguage): Boolean {
    val kotlinKeywords = setOf(
        "abstract", "actual", "annotation", "as", "break", "by", "catch", "class", 
        "companion", "const", "constructor", "continue", "crossinline", "data", 
        "delegate", "do", "dynamic", "else", "enum", "expect", "external", "false", 
        "final", "finally", "for", "fun", "get", "if", "import", "in", "infix", 
        "init", "inline", "inner", "interface", "internal", "is", "lateinit", 
        "noinline", "null", "object", "open", "operator", "out", "override", 
        "package", "private", "protected", "public", "reified", "return", "sealed", 
        "set", "super", "suspend", "tailrec", "this", "throw", "true", "try", 
        "typealias", "typeof", "val", "var", "vararg", "when", "where", "while"
    )
    
    val javaKeywords = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", 
        "class", "const", "continue", "default", "do", "double", "else", "enum", 
        "extends", "final", "finally", "float", "for", "goto", "if", "implements", 
        "import", "instanceof", "int", "interface", "long", "native", "new", "null", 
        "package", "private", "protected", "public", "return", "short", "static", 
        "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", 
        "transient", "try", "void", "volatile", "while", "true", "false"
    )
    
    val jsKeywords = setOf(
        "async", "await", "break", "case", "catch", "class", "const", "continue", 
        "debugger", "default", "delete", "do", "else", "export", "extends", "false", 
        "finally", "for", "function", "if", "import", "in", "instanceof", "let", 
        "new", "null", "return", "super", "switch", "this", "throw", "true", "try", 
        "typeof", "undefined", "var", "void", "while", "with", "yield"
    )
    
    val pythonKeywords = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break", 
        "class", "continue", "def", "del", "elif", "else", "except", "finally", 
        "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal", 
        "not", "or", "pass", "raise", "return", "try", "while", "with", "yield"
    )
    
    return when (language) {
        ProgrammingLanguage.KOTLIN -> kotlinKeywords.contains(word)
        ProgrammingLanguage.JAVA -> javaKeywords.contains(word)
        ProgrammingLanguage.JAVASCRIPT, ProgrammingLanguage.TYPESCRIPT -> jsKeywords.contains(word)
        ProgrammingLanguage.PYTHON -> pythonKeywords.contains(word)
        else -> false
    }
}