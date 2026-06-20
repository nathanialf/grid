package com.defnf.grid.presentation.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.defnf.grid.data.image.AudioArtRequest
import com.defnf.grid.presentation.util.FileType
import com.defnf.grid.presentation.util.getFileIcon
import com.defnf.grid.presentation.util.getFileType
import java.io.File

/**
 * Leading visual for a file row. When a local copy of an image or audio file is available,
 * it renders a real thumbnail (the picture, or the track's embedded album art); otherwise it
 * falls back to the file-type icon. Folders and non-thumbnailable types always show an icon.
 *
 * @param localFile the on-disk file to render a thumbnail from, or null if none is available
 *                  (uncached remote file). Callers pass null for files that should keep an icon.
 */
@Composable
fun FileThumbnail(
    fileName: String,
    isDirectory: Boolean,
    localFile: File?,
    size: Dp,
    folderTint: Color,
    fileTint: Color,
    modifier: Modifier = Modifier,
) {
    val icon = if (isDirectory) Icons.Default.Folder else getFileIcon(fileName)
    val tint = if (isDirectory) folderTint else fileTint

    val model: Any? = if (!isDirectory && localFile != null) {
        when (getFileType(fileName)) {
            FileType.IMAGE -> localFile
            FileType.AUDIO -> AudioArtRequest(localFile)
            else -> null
        }
    } else {
        null
    }

    if (model == null) {
        Icon(
            imageVector = icon,
            contentDescription = if (isDirectory) "Directory" else "File",
            tint = tint,
            modifier = modifier.size(size),
        )
    } else {
        SubcomposeAsyncImage(
            model = model,
            contentDescription = fileName,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp)),
            loading = { Icon(icon, null, tint = tint, modifier = Modifier.size(size)) },
            error = { Icon(icon, null, tint = tint, modifier = Modifier.size(size)) },
        )
    }
}
