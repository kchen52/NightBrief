package app.nightbrief.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowFormTest {
    @Test
    fun coverScreenStaysASingleColumn() {
        val form = WindowForm.of(widthDp = 360, heightDp = 800, landscape = false, fold = null)
        assertEquals(WindowForm.Navigation.Bar, form.navigation)
        assertEquals(WindowForm.Detail.Stacked, form.detail)
        assertEquals(1, form.weekColumns)
    }

    @Test
    fun unfoldedFoldPortraitUsesRailAndTwoColumns() {
        val fold = FoldSpan(leftPx = 340, rightPx = 360, vertical = true, separating = false, occludes = false)
        val form = WindowForm.of(widthDp = 700, heightDp = 900, landscape = false, fold = fold)
        assertEquals(WindowForm.Navigation.Rail, form.navigation)
        assertEquals(WindowForm.Detail.Panes, form.detail)
        assertEquals(2, form.weekColumns)
        assertEquals(fold, form.fold)
    }

    @Test
    fun widthAtTheMediumBreakpointUsesTheFoldLayout() {
        val at = WindowForm.of(widthDp = 600, heightDp = 480, landscape = false, fold = null)
        val below = WindowForm.of(widthDp = 599, heightDp = 900, landscape = false, fold = null)
        assertEquals(WindowForm.Detail.Panes, at.detail)
        assertEquals(WindowForm.Navigation.Rail, at.navigation)
        assertEquals(WindowForm.Detail.Stacked, below.detail)
        assertEquals(WindowForm.Navigation.Bar, below.navigation)
    }

    @Test
    fun landscapePhoneKeepsTheLeadSplit() {
        val form = WindowForm.of(widthDp = 800, heightDp = 400, landscape = true, fold = null)
        assertEquals(WindowForm.Navigation.Rail, form.navigation)
        assertEquals(WindowForm.Detail.Lead, form.detail)
        assertEquals(1, form.weekColumns)
    }

    @Test
    fun shortWideWindowStaysOnTheLeadSplit() {
        val form = WindowForm.of(widthDp = 700, heightDp = 479, landscape = false, fold = null)
        assertEquals(WindowForm.Detail.Lead, form.detail)
    }

    @Test
    fun tabletopKeepsTheBottomBar() {
        val fold = FoldSpan(leftPx = 0, rightPx = 800, vertical = false, separating = true, occludes = true)
        val form = WindowForm.of(widthDp = 800, heightDp = 800, landscape = true, fold = fold)
        assertEquals(WindowForm.Navigation.Bar, form.navigation)
        assertEquals(WindowForm.Detail.Stacked, form.detail)
        assertEquals(null, form.fold)
    }

    @Test
    fun flatHorizontalHingeStillUsesTheWideLayout() {
        val fold = FoldSpan(leftPx = 0, rightPx = 1000, vertical = false, separating = false, occludes = false)
        val form = WindowForm.of(widthDp = 700, heightDp = 900, landscape = false, fold = fold)
        assertEquals(WindowForm.Detail.Panes, form.detail)
        assertEquals(null, form.fold)
    }

    @Test
    fun centeredHingeSplitsTheColumnsEvenly() {
        val weights = paneWeights(
            contentLeftPx = 0,
            contentWidthPx = 1000,
            foldLeftPx = 490,
            foldRightPx = 510,
            occludes = false,
        )
        assertEquals(490f, weights?.left)
        assertEquals(490f, weights?.right)
        assertEquals(0, weights?.gapPx)
    }

    @Test
    fun occludingHingeBecomesTheGutter() {
        val weights = paneWeights(
            contentLeftPx = 80,
            contentWidthPx = 1000,
            foldLeftPx = 560,
            foldRightPx = 600,
            occludes = true,
        )
        assertEquals(480f, weights?.left)
        assertEquals(480f, weights?.right)
        assertEquals(40, weights?.gapPx)
    }

    @Test
    fun hingeOutsideTheContentKeepsEqualColumns() {
        assertEquals(
            null,
            paneWeights(
                contentLeftPx = 0,
                contentWidthPx = 1000,
                foldLeftPx = 2000,
                foldRightPx = 2020,
                occludes = true,
            ),
        )
    }

    @Test
    fun hingeTooCloseToTheEdgeKeepsEqualColumns() {
        assertEquals(
            null,
            paneWeights(
                contentLeftPx = 0,
                contentWidthPx = 1000,
                foldLeftPx = 50,
                foldRightPx = 70,
                occludes = false,
            ),
        )
    }
}
