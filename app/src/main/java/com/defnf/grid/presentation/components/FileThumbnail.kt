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
import com.defnf.grid.data.image.VideoFrameRequest
import com.defnf.grid.presentation.util.FileType
import com.defnf.grid.presentation.util.getFileIcon
import com.defnf.grid.presentation.util.getFileType
import java.io.File

/**
 * Builds the Coil model used to render a real thumbnail for a file, or null when the file should
 * keep its type icon. A real thumbnail is only available for cached (on-disk) images, audio (album
 * art), and video (first frame); folders and everything else return null.
 */
private fun thumbnailModel(fileName: String, isDirectory: Boolean, localFile: File?): Any? {
    if (isDirectory || localFile == null) return null
    return when (getFileType(fileName)) {
        FileType.IMAGE -> localFile
        FileType.AUDIO -> AudioArtRequest(localFile)
        FileType.VIDEO -> VideoFrameRequest(localFile)
        else -> null
    }
}

/**
 * Whether a real thumbnail (not just an icon) can be rendered for this file. Callers use this to
 * decide between a full-bleed thumbnail layout and the plain icon layout.
 */
fun hasThumbnail(fileName: String, isDirectory: Boolean, localFile: File?): Boolean =
    thumbnailModel(fileName, isDirectory, localFile) != null

/**
 * Leading visual for a file row. When a local copy of an image, audio, or video file is
 * available, it renders a real thumbnail (the picture, the track's embedded album art, or the
 * video's first frame); otherwise it falls back to the file-type icon. Folders and
 * non-thumbnailable types always show an icon.
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

    val model = thumbnailModel(fileName, isDirectory, localFile)

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

/**
 * Renders a file's real thumbnail filling whatever bounds [modifier] gives it (cropped to fill),
 * with no fixed size. Intended for full-bleed layouts (grid cards, edge-to-edge list rows) where
 * the caller has already confirmed [hasThumbnail]. If no thumbnail is available it falls back to a
 * centered type icon so it degrades gracefully.
 */
@Composable
fun FileThumbnailFill(
    fileName: String,
    localFile: File?,
    modifier: Modifier = Modifier,
    fileTint: Color = Color.Unspecified,
) {
    val icon = getFileIcon(fileName)
    val model = thumbnailModel(fileName, isDirectory = false, localFile = localFile)

    if (model == null) {
        Icon(
            imageVector = icon,
            contentDescription = fileName,
            tint = fileTint,
            modifier = modifier.size(48.dp),
        )
    } else {
        SubcomposeAsyncImage(
            model = model,
            contentDescription = fileName,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            loading = { Icon(icon, null, tint = fileTint, modifier = Modifier.size(48.dp)) },
            error = { Icon(icon, null, tint = fileTint, modifier = Modifier.size(48.dp)) },
        )
    }
}
