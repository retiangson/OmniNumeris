package com.omninumeris.app.notebook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omninumeris.app.R
import com.omninumeris.application.NotebookPageThumbnail
import com.omninumeris.application.NotebookRead
import com.omninumeris.domain.NotebookPage
import com.omninumeris.domain.PlacedNotebookText
import com.omninumeris.domain.canvas.InkPoint
import java.util.UUID

@Composable
internal fun NotebookPageThumbnails(
    model: NotebookViewModel,
    page: NotebookPage,
    enabled: Boolean,
    open: (UUID) -> Unit,
) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = page.position)
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(112.dp),
        state = list,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(page.pageCount) { position ->
            val preview by produceState<NotebookRead<NotebookPageThumbnail>?>(
                null, page, position,
            ) {
                value = model.thumbnail(page.notebook.id, position)
            }
            val thumbnail = (preview as? NotebookRead.Available)?.value
            TextButton(
                onClick = { thumbnail?.let { open(it.pageId) } },
                enabled = enabled && thumbnail != null,
                modifier = Modifier.semantics { selected = thumbnail?.pageId == page.id },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ThumbnailCanvas(thumbnail, thumbnail?.pageId == page.id)
                    Text(stringResource(R.string.notebook_thumbnail_page, position + 1))
                }
            }
        }
    }
}

@Composable
private fun ThumbnailCanvas(thumbnail: NotebookPageThumbnail?, selected: Boolean) {
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val paper = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.onSurface
    val result = MaterialTheme.colorScheme.tertiary
    Canvas(Modifier.size(48.dp, 62.dp).border(1.dp, outline).background(paper).padding(2.dp).clipToBounds()) {
        val xScale = size.width / NotebookPage.WIDTH
        val yScale = size.height / NotebookPage.HEIGHT
        fun InkPoint.offset() = Offset(x.toFloat() * xScale, y.toFloat() * yScale)
        thumbnail?.ink?.forEach { stroke ->
            if (stroke.size == 1) drawCircle(ink, 0.75.dp.toPx(), stroke.single().offset())
            else for (index in 1 until stroke.size) {
                drawLine(ink, stroke[index - 1].offset(), stroke[index].offset(), 0.75.dp.toPx())
            }
        }
        thumbnail?.results?.forEach { position ->
            drawRect(result, position.offset(), Size(PlacedNotebookText.WIDTH * xScale, 3.dp.toPx()))
        }
    }
}
