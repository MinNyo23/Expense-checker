package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.data.model.MovieItem
import com.example.ui.components.MovieWatchlistDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MovieWatchlistUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `movie watchlist dialog adds movie and moves watched movie to finished tab`() {
        var nextId = 2
        composeTestRule.setContent {
            var moviesState by remember {
                mutableStateOf(
                    listOf(
                        MovieItem(
                            id = "m-1",
                            title = "Your Name",
                            isWatched = false,
                            addedBy = "Min Nyo",
                            addedAt = "2026-11-19"
                        )
                    )
                )
            }
            MovieWatchlistDialog(
                movies = moviesState,
                onAddMovie = { title, note ->
                    val added = MovieItem(
                        id = "m-${nextId++}",
                        title = title,
                        note = note,
                        isWatched = false,
                        addedBy = "Min Nyo"
                    )
                    moviesState = listOf(added) + moviesState
                },
                onMarkWatched = { movieId ->
                    moviesState = moviesState.map {
                        if (it.id == movieId) it.copy(isWatched = true, watchedAt = "2026-11-19") else it
                    }
                },
                onMoveToWatchLater = { movieId ->
                    moviesState = moviesState.map {
                        if (it.id == movieId) it.copy(isWatched = false, watchedAt = "") else it
                    }
                },
                onDeleteMovie = { movieId ->
                    moviesState = moviesState.filterNot { it.id == movieId }
                },
                onDismiss = {}
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("movie_dialog_list").performScrollToNode(hasTestTag("movie_item_card_m-1"))
        composeTestRule.onNodeWithTag("movie_item_card_m-1").assertExists()

        // Add a new movie via the Watch Later input
        composeTestRule.onNodeWithTag("movie_dialog_list").performScrollToNode(hasTestTag("movie_title_input"))
        composeTestRule.onNodeWithTag("movie_title_input").performTextInput("Past Lives")
        composeTestRule.onNodeWithTag("movie_dialog_list").performScrollToNode(hasTestTag("add_movie_button"))
        composeTestRule.onNodeWithTag("add_movie_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("movie_dialog_list").performScrollToNode(hasTestTag("movie_item_card_m-2"))
        composeTestRule.onNodeWithTag("movie_item_card_m-2").assertExists()

        // Mark both movies as finished
        composeTestRule.onNodeWithTag("movie_dialog_list").performScrollToNode(hasTestTag("movie_item_card_m-1"))
        composeTestRule.onNodeWithTag("mark_movie_watched_m-1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("movie_dialog_list").performScrollToNode(hasTestTag("movie_item_card_m-2"))
        composeTestRule.onNodeWithTag("mark_movie_watched_m-2").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("movie_dialog_list").performScrollToNode(hasTestTag("movie_empty_watch_later"))
        composeTestRule.onNodeWithTag("movie_empty_watch_later").assertExists()

        // Switch to Finished tab and verify moved movies are present there
        composeTestRule.onNodeWithTag("movie_dialog_list").performScrollToNode(hasTestTag("movie_tab_finished"))
        composeTestRule.onNodeWithTag("movie_tab_finished").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("movie_dialog_list").performScrollToNode(hasTestTag("movie_item_card_m-1"))
        composeTestRule.onNodeWithTag("movie_item_card_m-1").assertExists()
        composeTestRule.onNodeWithTag("movie_dialog_list").performScrollToNode(hasTestTag("movie_item_card_m-2"))
        composeTestRule.onNodeWithTag("movie_item_card_m-2").assertExists()
        composeTestRule.onNodeWithTag("movie_dialog_list").performScrollToNode(hasTestTag("movie_item_card_m-1"))
        composeTestRule.onNodeWithTag("move_movie_watch_later_m-1").assertExists()
    }
}
