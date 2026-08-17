package com.ekkus93.silentcaption.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

@Composable
fun readerScreen(
    state: ReaderTranscriptState,
    onScrolledBackward: () -> Unit,
    onJumpToLive: () -> Unit,
    onClear: () -> Unit,
) {
    val listState = rememberLazyListState()
    val itemCount = state.committed.size + if (state.currentCaption.isBlank()) 0 else 1

    LaunchedEffect(state.followingLive, itemCount) {
        if (state.followingLive && itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .drop(1)
            .collect {
                if (state.followingLive && !listState.isAtLiveEdge(itemCount)) {
                    onScrolledBackward()
                }
            }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            readerHeader(state, onJumpToLive, onClear)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    count = state.committed.size,
                    key = { index -> state.committed[index].id },
                ) { index ->
                    Text(
                        text = state.committed[index].text,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (state.currentCaption.isNotBlank()) {
                    item(key = "current-caption") {
                        Text(
                            text = state.currentCaption,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun readerHeader(
    state: ReaderTranscriptState,
    onJumpToLive: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Reader", style = MaterialTheme.typography.headlineMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!state.followingLive) {
                Button(onClick = onJumpToLive) { Text("Jump to Live") }
            }
            Button(
                onClick = onClear,
                enabled = state.committed.isNotEmpty() || state.currentCaption.isNotBlank(),
            ) {
                Text("Clear")
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListState.isAtLiveEdge(itemCount: Int): Boolean {
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index
    return itemCount == 0 || lastVisible == null || lastVisible >= itemCount - 1
}
