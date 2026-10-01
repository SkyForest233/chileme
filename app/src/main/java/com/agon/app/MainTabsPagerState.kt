// Copyright 2026, compose-miuix-ui contributors
// Adapted by chileme contributors from the Miuix v0.9.4 example's MainPagerState.
// SPDX-License-Identifier: Apache-2.0

package com.agon.app

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.agon.app.ui.theme.MotionSpring
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.utils.springAnimateToPage

/**
 * Keeps the selected tab (the navigation target) separate from PagerState.currentPage while a
 * multi-page spring scroll passes intermediate pages. This mirrors the Miuix demo's controller;
 * Compose's animateScrollToPage can pre-jump when the destination is several pages away.
 */
@Stable
internal class MainTabsPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navigationJob: Job? = null

    fun animateToPage(targetIndex: Int, animatePager: suspend () -> Unit) {
        if (targetIndex == selectedPage) return

        navigationJob?.cancel()
        selectedPage = targetIndex
        isNavigating = true

        navigationJob = coroutineScope.launch {
            val thisJob = coroutineContext.job
            try {
                animatePager()
            } finally {
                if (navigationJob == thisJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetIndex) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

internal suspend fun PagerState.animateMainTabToPage(targetIndex: Int, isMiuix: Boolean) {
    if (isMiuix) {
        springAnimateToPage(targetIndex)
    } else {
        val distance = abs(targetIndex - currentPage)
        animateScrollToPage(targetIndex, animationSpec = MotionSpring.page<Float>(distance))
    }
}

@Composable
internal fun rememberMainTabsPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): MainTabsPagerState = remember(pagerState, coroutineScope) {
    MainTabsPagerState(pagerState, coroutineScope)
}
