package com.github.beng420.kung.update;

import static org.junit.Assert.*;

import com.github.beng420.kung.ui.UiBounds;
import org.junit.Test;

public final class KungUpdateToastStateTest {
    @Test
    public void slidesInStaysEightSecondsAndSlidesOutWithoutDependingOnTicks() {
        var state = new KungUpdateToastState();
        state.show(10_000L);
        assertEquals(640F, state.layout(640, 360).x(), 0.001F);
        assertEquals(1F, state.remaining(), 0.001F);
        state.advance(10_125L, true, false);
        assertEquals(516F, state.layout(640, 360).x(), 0.001F);
        state.advance(10_250L, true, false);
        assertEquals(392F, state.layout(640, 360).x(), 0.001F);
        state.advance(14_250L, true, false);
        assertEquals(0.5F, state.remaining(), 0.001F);
        state.advance(18_250L, true, false);
        assertEquals(0F, state.remaining(), 0.001F);
        assertEquals(392F, state.layout(640, 360).x(), 0.001F);
        state.advance(18_375L, true, false);
        assertEquals(516F, state.layout(640, 360).x(), 0.001F);
        state.advance(18_500L, true, false);
        assertFalse(state.active());
        assertEquals(KungUpdateToastState.Action.NONE, state.hitTest(state.layout(640, 360), 630, 230, 0));
    }

    @Test
    public void hoveringPausesCountdownButNotEntryOrExitAnimation() {
        var state = new KungUpdateToastState();
        state.show(0L);
        state.advance(250L, true, true);
        assertEquals(392F, state.layout(640, 360).x(), 0.001F);
        state.advance(60_250L, true, true);
        assertEquals(1F, state.remaining(), 0.001F);
        state.advance(68_250L, true, false);
        assertEquals(0F, state.remaining(), 0.001F);
        state.advance(68_500L, true, true);
        assertFalse(state.active());
    }

    @Test
    public void hiddenHudAndLoadingDoNotUseUpVisibleTimeOrCauseCatchUpOnReturn() {
        var state = new KungUpdateToastState();
        state.show(0L);
        state.advance(30_000L, false, false);
        assertEquals(640F, state.layout(640, 360).x(), 0.001F);
        state.advance(30_250L, true, false);
        state.advance(31_250L, true, false);
        assertEquals(0.875F, state.remaining(), 0.001F);
        state.advance(90_000L, false, false);
        state.advance(90_000L, true, false);
        assertEquals(0.875F, state.remaining(), 0.001F);
        state.advance(97_250L, true, false);
        assertFalse(state.active());
    }

    @Test
    public void dismissAndReplacementCannotLeaveAnInvisibleClickTarget() {
        var state = new KungUpdateToastState();
        state.show(0L);
        state.advance(250L, true, false);
        var layout = state.layout(640, 360);
        assertEquals(KungUpdateToastState.Action.DISMISS, click(state, layout, KungUpdateToastState.CLOSE, 0));
        state.clear();
        assertEquals(KungUpdateToastState.Action.NONE, click(state, layout, KungUpdateToastState.CLOSE, 0));
        state.show(50_000L);
        assertEquals(1F, state.remaining(), 0.001F);
        assertEquals(640F, state.layout(640, 360).x(), 0.001F);
        state.advance(50_250L, true, false);
        assertEquals(KungUpdateToastState.Action.UPDATES, click(state, state.layout(640, 360), KungUpdateToastState.UPDATES, 0));
    }

    @Test
    public void onlyTheVisibleCardConsumesClicksAndButtonsUseTheDrawingGeometry() {
        var state = new KungUpdateToastState();
        state.show(0L);
        state.advance(250L, true, false);
        var layout = state.layout(640, 360);
        assertEquals(KungUpdateToastState.Action.UPDATES, click(state, layout, KungUpdateToastState.UPDATES, 0));
        assertEquals(KungUpdateToastState.Action.GITHUB, click(state, layout, KungUpdateToastState.GITHUB, 0));
        assertEquals(KungUpdateToastState.Action.BLOCK, click(state, layout, KungUpdateToastState.GITHUB, 1));
        assertEquals(KungUpdateToastState.Action.BLOCK, state.hitTest(layout, layout.x() + 4, layout.y() + 4, 0));
        assertEquals(KungUpdateToastState.Action.NONE, state.hitTest(layout, layout.x() - 1, layout.y(), 0));
        assertEquals(KungUpdateToastState.Action.NONE, state.hitTest(layout, 630, layout.y(), 0));
        assertEquals(KungUpdateToastState.Action.NONE, state.hitTest(layout, layout.x(), 328, 0));
    }

    @Test
    public void resizeKeepsCardAboveChatAndScalesDrawingAndClicksTogether() {
        var state = new KungUpdateToastState();
        state.show(0L);
        state.advance(250L, true, false);
        for (int[] size : new int[][] {{640, 360}, {320, 240}, {220, 180}, {160, 120}, {960, 540}}) {
            var layout = state.layout(size[0], size[1]);
            assertTrue(layout.x() >= 0F);
            assertTrue(layout.y() >= 0F);
            assertEquals(size[0] - 10F, layout.x() + KungUpdateToastState.WIDTH * layout.scale(), 0.001F);
            assertEquals(size[1] - 32F, layout.y() + KungUpdateToastState.HEIGHT * layout.scale(), 0.001F);
            assertEquals(KungUpdateToastState.Action.UPDATES, click(state, layout, KungUpdateToastState.UPDATES, 0));
            assertEquals(KungUpdateToastState.Action.GITHUB, click(state, layout, KungUpdateToastState.GITHUB, 0));
        }
    }

    private static KungUpdateToastState.Action click(KungUpdateToastState state, KungUpdateToastState.Layout layout,
        UiBounds bounds, int button) {
        return state.hitTest(layout, layout.x() + (bounds.x() + bounds.width() / 2F) * layout.scale(),
            layout.y() + (bounds.y() + bounds.height() / 2F) * layout.scale(), button);
    }
}
