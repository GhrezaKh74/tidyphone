package app.tidyphone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tidyphone.domain.model.FileCategory
import app.tidyphone.ui.theme.CategoryColors
import coil3.compose.SubcomposeAsyncImage
import java.io.File

fun categoryOfName(name: String): FileCategory = FileCategory.of(null, name.substringAfterLast('.', ""))

fun FileCategory.icon(): ImageVector = when (this) {
    FileCategory.IMAGE -> Icons.Rounded.Image
    FileCategory.VIDEO -> Icons.Rounded.Movie
    FileCategory.AUDIO -> Icons.Rounded.AudioFile
    FileCategory.DOCUMENT -> Icons.Rounded.Description
    FileCategory.ARCHIVE -> Icons.Rounded.Inventory2
    FileCategory.APK -> Icons.Rounded.Android
    FileCategory.OTHER -> Icons.Rounded.InsertDriveFile
}

fun FileCategory.color(): Color = when (this) {
    FileCategory.IMAGE -> CategoryColors.Images
    FileCategory.VIDEO -> CategoryColors.Videos
    FileCategory.AUDIO -> CategoryColors.Audio
    FileCategory.DOCUMENT -> CategoryColors.Documents
    FileCategory.ARCHIVE -> CategoryColors.Archives
    FileCategory.APK -> CategoryColors.Apks
    FileCategory.OTHER -> CategoryColors.Other
}

/** Real thumbnail for images and videos (via Coil), a tinted icon for everything else. */
@Composable
fun FileThumb(path: String, modifier: Modifier = Modifier, size: Dp = 48.dp, isFolder: Boolean = false) {
    val cat = categoryOfName(path.substringAfterLast('/'))
    val shape = RoundedCornerShape(size * 0.28f)
    val fallback = @Composable {
        Box(Modifier.size(size).clip(shape).background(cat.color().copy(alpha = .15f)), contentAlignment = Alignment.Center) {
            Icon(if (isFolder) Icons.Rounded.FolderOpen else cat.icon(), null, tint = cat.color(), modifier = Modifier.size(size * .5f))
        }
    }
    if (!isFolder && (cat == FileCategory.IMAGE || cat == FileCategory.VIDEO)) {
        Box(modifier.size(size).clip(shape)) {
            SubcomposeAsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
                loading = { fallback() },
                error = { fallback() },
            )
            if (cat == FileCategory.VIDEO) {
                Icon(Icons.Rounded.PlayCircle, null, tint = Color.White, modifier = Modifier.size(size * .38f).align(Alignment.Center))
            }
        }
    } else {
        Box(modifier) { fallback() }
    }
}
