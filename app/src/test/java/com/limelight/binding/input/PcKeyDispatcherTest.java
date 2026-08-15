package com.limelight.binding.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import com.limelight.nvstream.input.KeyboardPacket;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class PcKeyDispatcherTest {
    @Test
    public void keyboardTranslatorMapsF1ThroughF12ToWindowsVirtualKeys() {
        KeyboardTranslator translator = new KeyboardTranslator();

        for (int offset = 0; offset < 12; offset++) {
            int androidKeyCode = KeyEvent.KEYCODE_F1 + offset;
            // Anchor the expected Windows VK independently from production constants.
            int expectedKeyMap = 0x8070 + offset;

            assertEquals("Unexpected mapping for F" + (offset + 1),
                    expectedKeyMap, translator.translate(androidKeyCode, -1) & 0xFFFF);
        }
    }

    @Test
    public void functionKeysUseTheGenericTapPipeline() {
        FakeSender sender = new FakeSender();
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        for (int offset = 0; offset < 12; offset++) {
            int androidKeyCode = KeyEvent.KEYCODE_F1 + offset;
            assertTrue("F" + (offset + 1) + " should be sent", dispatcher.tapKey(androidKeyCode));
        }

        assertEquals(12, sender.baseReadCount);
        assertEquals(12, sender.translateCalls);
        assertEquals(24, sender.events.size());
        for (int offset = 0; offset < 12; offset++) {
            int androidKeyCode = KeyEvent.KEYCODE_F1 + offset;
            assertEquals(event(androidKeyCode, KeyboardPacket.KEY_DOWN, 0),
                    sender.events.get(offset * 2));
            assertEquals(event(androidKeyCode, KeyboardPacket.KEY_UP, 0),
                    sender.events.get(offset * 2 + 1));
        }
        assertAllFlagsNormalized(sender.events);
    }

    @Test
    public void functionKeyPreservesLatchedCtrlAndShift() {
        FakeSender sender = new FakeSender();
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        assertTrue(dispatcher.toggleModifier(PcKeyDispatcher.Modifier.CTRL));
        assertTrue(dispatcher.toggleModifier(PcKeyDispatcher.Modifier.SHIFT));
        assertTrue(dispatcher.tapKey(KeyEvent.KEYCODE_F12));
        assertTrue(dispatcher.isModifierLatched(PcKeyDispatcher.Modifier.CTRL));
        assertTrue(dispatcher.isModifierLatched(PcKeyDispatcher.Modifier.SHIFT));
        dispatcher.releaseAll();

        assertEvents(sender,
                event(KeyEvent.KEYCODE_CTRL_LEFT, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_CTRL),
                event(KeyEvent.KEYCODE_SHIFT_LEFT, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT),
                event(KeyEvent.KEYCODE_F12, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT),
                event(KeyEvent.KEYCODE_F12, KeyboardPacket.KEY_UP,
                        KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT),
                event(KeyEvent.KEYCODE_SHIFT_LEFT, KeyboardPacket.KEY_UP,
                        KeyboardPacket.MODIFIER_CTRL),
                event(KeyEvent.KEYCODE_CTRL_LEFT, KeyboardPacket.KEY_UP, 0));
    }

    @Test
    public void standaloneWinSendsDownAndUpWithNormalizedFlags() {
        FakeSender sender = new FakeSender();
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        assertTrue(dispatcher.tapKey(KeyEvent.KEYCODE_META_LEFT));

        assertEquals(1, sender.baseReadCount);
        assertEvents(sender,
                event(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_META),
                event(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_UP, 0));
    }

    @Test
    public void standaloneWinIsNoOpWhenBaseAlreadyContainsMeta() {
        FakeSender sender = new FakeSender();
        sender.baseModifiers = KeyboardPacket.MODIFIER_META;
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        assertFalse(dispatcher.tapKey(KeyEvent.KEYCODE_META_LEFT));

        assertEquals(1, sender.baseReadCount);
        assertEquals(0, sender.translateCalls);
        assertTrue(sender.events.isEmpty());
    }

    @Test
    public void winESendsExpectedSequence() {
        FakeSender sender = new FakeSender();
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        assertTrue(dispatcher.dispatchShortcut(PcKeyDispatcher.PcShortcut.WIN_E));

        assertEvents(sender,
                event(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_META),
                event(KeyEvent.KEYCODE_E, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_META),
                event(KeyEvent.KEYCODE_E, KeyboardPacket.KEY_UP,
                        KeyboardPacket.MODIFIER_META),
                event(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_UP, 0));
    }

    @Test
    public void winRSendsExpectedSequence() {
        FakeSender sender = new FakeSender();
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        assertTrue(dispatcher.dispatchShortcut(PcKeyDispatcher.PcShortcut.WIN_R));

        assertEvents(sender,
                event(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_META),
                event(KeyEvent.KEYCODE_R, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_META),
                event(KeyEvent.KEYCODE_R, KeyboardPacket.KEY_UP,
                        KeyboardPacket.MODIFIER_META),
                event(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_UP, 0));
    }

    @Test
    public void previousWindowSendsAltTab() {
        FakeSender sender = new FakeSender();
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        assertTrue(dispatcher.dispatchShortcut(PcKeyDispatcher.PcShortcut.PREVIOUS_WINDOW));

        assertEvents(sender,
                event(KeyEvent.KEYCODE_ALT_LEFT, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_ALT),
                event(KeyEvent.KEYCODE_TAB, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_ALT),
                event(KeyEvent.KEYCODE_TAB, KeyboardPacket.KEY_UP,
                        KeyboardPacket.MODIFIER_ALT),
                event(KeyEvent.KEYCODE_ALT_LEFT, KeyboardPacket.KEY_UP, 0));
    }

    @Test
    public void taskManagerSendsCtrlShiftEscAndReleasesInReverseOrder() {
        FakeSender sender = new FakeSender();
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        assertTrue(dispatcher.dispatchShortcut(PcKeyDispatcher.PcShortcut.TASK_MANAGER));

        assertEvents(sender,
                event(KeyEvent.KEYCODE_CTRL_LEFT, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_CTRL),
                event(KeyEvent.KEYCODE_SHIFT_LEFT, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT),
                event(KeyEvent.KEYCODE_ESCAPE, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT),
                event(KeyEvent.KEYCODE_ESCAPE, KeyboardPacket.KEY_UP,
                        KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT),
                event(KeyEvent.KEYCODE_SHIFT_LEFT, KeyboardPacket.KEY_UP,
                        KeyboardPacket.MODIFIER_CTRL),
                event(KeyEvent.KEYCODE_CTRL_LEFT, KeyboardPacket.KEY_UP, 0));
    }

    @Test
    public void winEReusesBaseMetaWithoutOwningOrReleasingIt() {
        FakeSender sender = new FakeSender();
        sender.baseModifiers = KeyboardPacket.MODIFIER_META;
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        assertTrue(dispatcher.dispatchShortcut(PcKeyDispatcher.PcShortcut.WIN_E));

        assertEquals(1, sender.baseReadCount);
        assertEquals(listOf(KeyEvent.KEYCODE_E), sender.translatedAndroidKeys);
        assertEvents(sender,
                event(KeyEvent.KEYCODE_E, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_META),
                event(KeyEvent.KEYCODE_E, KeyboardPacket.KEY_UP,
                        KeyboardPacket.MODIFIER_META));
    }

    @Test
    public void taskManagerOnlyOwnsShiftWhenBaseContainsCtrl() {
        FakeSender sender = new FakeSender();
        sender.baseModifiers = KeyboardPacket.MODIFIER_CTRL;
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        assertTrue(dispatcher.dispatchShortcut(PcKeyDispatcher.PcShortcut.TASK_MANAGER));

        assertEquals(1, sender.baseReadCount);
        assertEquals(listOf(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_ESCAPE),
                sender.translatedAndroidKeys);
        assertEvents(sender,
                event(KeyEvent.KEYCODE_SHIFT_LEFT, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT),
                event(KeyEvent.KEYCODE_ESCAPE, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT),
                event(KeyEvent.KEYCODE_ESCAPE, KeyboardPacket.KEY_UP,
                        KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT),
                event(KeyEvent.KEYCODE_SHIFT_LEFT, KeyboardPacket.KEY_UP,
                        KeyboardPacket.MODIFIER_CTRL));
    }

    @Test
    public void latchedCtrlAppliesToLaterTapUntilExplicitlyReleased() {
        FakeSender sender = new FakeSender();
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        assertTrue(dispatcher.toggleModifier(PcKeyDispatcher.Modifier.CTRL));
        assertTrue(dispatcher.isModifierLatched(PcKeyDispatcher.Modifier.CTRL));
        assertTrue(dispatcher.tapKey(KeyEvent.KEYCODE_A));
        assertTrue(dispatcher.toggleModifier(PcKeyDispatcher.Modifier.CTRL));
        assertFalse(dispatcher.isModifierLatched(PcKeyDispatcher.Modifier.CTRL));

        assertEquals(3, sender.baseReadCount);
        assertEvents(sender,
                event(KeyEvent.KEYCODE_CTRL_LEFT, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_CTRL),
                event(KeyEvent.KEYCODE_A, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_CTRL),
                event(KeyEvent.KEYCODE_A, KeyboardPacket.KEY_UP,
                        KeyboardPacket.MODIFIER_CTRL),
                event(KeyEvent.KEYCODE_CTRL_LEFT, KeyboardPacket.KEY_UP, 0));
    }

    @Test
    public void latchedCtrlAndShiftReleaseInReverseOrder() {
        FakeSender sender = new FakeSender();
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        assertTrue(dispatcher.toggleModifier(PcKeyDispatcher.Modifier.CTRL));
        assertTrue(dispatcher.toggleModifier(PcKeyDispatcher.Modifier.SHIFT));
        dispatcher.releaseAll();
        dispatcher.releaseAll();

        assertFalse(dispatcher.isModifierLatched(PcKeyDispatcher.Modifier.CTRL));
        assertFalse(dispatcher.isModifierLatched(PcKeyDispatcher.Modifier.SHIFT));
        assertEvents(sender,
                event(KeyEvent.KEYCODE_CTRL_LEFT, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_CTRL),
                event(KeyEvent.KEYCODE_SHIFT_LEFT, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT),
                event(KeyEvent.KEYCODE_SHIFT_LEFT, KeyboardPacket.KEY_UP,
                        KeyboardPacket.MODIFIER_CTRL),
                event(KeyEvent.KEYCODE_CTRL_LEFT, KeyboardPacket.KEY_UP, 0));
    }

    @Test
    public void atomicShortcutDoesNotReleaseMatchingLatchedModifier() {
        FakeSender sender = new FakeSender();
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        assertTrue(dispatcher.toggleModifier(PcKeyDispatcher.Modifier.CTRL));
        assertTrue(dispatcher.dispatchShortcut(PcKeyDispatcher.PcShortcut.TASK_MANAGER));
        assertTrue(dispatcher.isModifierLatched(PcKeyDispatcher.Modifier.CTRL));

        assertEvents(sender,
                event(KeyEvent.KEYCODE_CTRL_LEFT, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_CTRL),
                event(KeyEvent.KEYCODE_SHIFT_LEFT, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT),
                event(KeyEvent.KEYCODE_ESCAPE, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT),
                event(KeyEvent.KEYCODE_ESCAPE, KeyboardPacket.KEY_UP,
                        KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT),
                event(KeyEvent.KEYCODE_SHIFT_LEFT, KeyboardPacket.KEY_UP,
                        KeyboardPacket.MODIFIER_CTRL));

        dispatcher.releaseAll();
        assertEquals(event(KeyEvent.KEYCODE_CTRL_LEFT, KeyboardPacket.KEY_UP, 0),
                sender.events.get(sender.events.size() - 1));
    }

    @Test
    public void latchIsRejectedWhenPhysicalBaseAlreadyOwnsModifier() {
        FakeSender sender = new FakeSender();
        sender.baseModifiers = KeyboardPacket.MODIFIER_CTRL;
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        assertFalse(dispatcher.toggleModifier(PcKeyDispatcher.Modifier.CTRL));

        assertFalse(dispatcher.isModifierLatched(PcKeyDispatcher.Modifier.CTRL));
        assertEquals(1, sender.baseReadCount);
        assertEquals(0, sender.translateCalls);
        assertTrue(sender.events.isEmpty());
    }

    @Test
    public void disablingLatchTransfersOwnershipToHeldPhysicalModifier() {
        FakeSender sender = new FakeSender();
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        assertTrue(dispatcher.toggleModifier(PcKeyDispatcher.Modifier.CTRL));
        sender.baseModifiers = KeyboardPacket.MODIFIER_CTRL;
        sender.exactBaseKeys.add(KeyEvent.KEYCODE_CTRL_LEFT);
        int eventCountBeforeHandoff = sender.events.size();

        assertTrue(dispatcher.toggleModifier(PcKeyDispatcher.Modifier.CTRL));

        assertFalse(dispatcher.isModifierLatched(PcKeyDispatcher.Modifier.CTRL));
        assertEquals(eventCountBeforeHandoff, sender.events.size());
        assertEvents(sender,
                event(KeyEvent.KEYCODE_CTRL_LEFT, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_CTRL));
    }

    @Test
    public void rightPhysicalCtrlDoesNotStealLeftSyntheticOwnership() {
        FakeSender sender = new FakeSender();
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        assertTrue(dispatcher.toggleModifier(PcKeyDispatcher.Modifier.CTRL));
        // Aggregate BASE says Ctrl, but only the right-hand physical key is actually down.
        sender.baseModifiers = KeyboardPacket.MODIFIER_CTRL;
        sender.exactBaseKeys.add(KeyEvent.KEYCODE_CTRL_RIGHT);

        assertTrue(dispatcher.toggleModifier(PcKeyDispatcher.Modifier.CTRL));

        assertFalse(dispatcher.isModifierLatched(PcKeyDispatcher.Modifier.CTRL));
        assertEvents(sender,
                event(KeyEvent.KEYCODE_CTRL_LEFT, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_CTRL),
                event(KeyEvent.KEYCODE_CTRL_LEFT, KeyboardPacket.KEY_UP,
                        KeyboardPacket.MODIFIER_CTRL));
    }

    @Test
    public void magnifierShortcutsUseTranslatorAndExpectedMetaChords() {
        FakeSender sender = new FakeSender();
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        assertTrue(dispatcher.dispatchShortcut(PcKeyDispatcher.PcShortcut.MAGNIFIER_ZOOM_IN));
        assertTrue(dispatcher.dispatchShortcut(PcKeyDispatcher.PcShortcut.MAGNIFIER_ZOOM_OUT));
        assertTrue(dispatcher.dispatchShortcut(PcKeyDispatcher.PcShortcut.MAGNIFIER_CLOSE));

        assertEquals(listOf(
                        KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_EQUALS,
                        KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_MINUS,
                        KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_ESCAPE),
                sender.translatedAndroidKeys);
        assertEquals(12, sender.events.size());
        assertMetaChord(sender.events, 0, KeyEvent.KEYCODE_EQUALS);
        assertMetaChord(sender.events, 4, KeyEvent.KEYCODE_MINUS);
        assertMetaChord(sender.events, 8, KeyEvent.KEYCODE_ESCAPE);
    }

    @Test
    public void failedLatchDownIsUnwoundAndDoesNotRemainSelected() {
        FakeSender sender = new FakeSender();
        sender.failOnceOnSendNumber = 0;
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        assertFalse(dispatcher.toggleModifier(PcKeyDispatcher.Modifier.SHIFT));

        assertFalse(dispatcher.isModifierLatched(PcKeyDispatcher.Modifier.SHIFT));
        assertEvents(sender,
                event(KeyEvent.KEYCODE_SHIFT_LEFT, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_SHIFT),
                event(KeyEvent.KEYCODE_SHIFT_LEFT, KeyboardPacket.KEY_UP, 0));
        int eventCount = sender.events.size();
        dispatcher.releaseAll();
        assertEquals(eventCount, sender.events.size());
    }

    @Test
    public void failedUnlatchRemainsSelectedAndCanRetryKeyUp() {
        FakeSender sender = new FakeSender();
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);
        assertTrue(dispatcher.toggleModifier(PcKeyDispatcher.Modifier.CTRL));
        sender.failOnceOnSendNumber = 1;

        assertFalse(dispatcher.toggleModifier(PcKeyDispatcher.Modifier.CTRL));
        assertTrue(dispatcher.isModifierLatched(PcKeyDispatcher.Modifier.CTRL));

        assertTrue(dispatcher.toggleModifier(PcKeyDispatcher.Modifier.CTRL));
        assertFalse(dispatcher.isModifierLatched(PcKeyDispatcher.Modifier.CTRL));
        assertEvents(sender,
                event(KeyEvent.KEYCODE_CTRL_LEFT, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_CTRL),
                event(KeyEvent.KEYCODE_CTRL_LEFT, KeyboardPacket.KEY_UP, 0),
                event(KeyEvent.KEYCODE_CTRL_LEFT, KeyboardPacket.KEY_UP, 0));
    }

    @Test
    public void disconnectWhileLatchedClearsLocalStateWithoutPackets() {
        FakeSender sender = new FakeSender();
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);
        assertTrue(dispatcher.toggleModifier(PcKeyDispatcher.Modifier.CTRL));
        int eventCountBeforeDisconnect = sender.events.size();

        sender.ready = false;
        dispatcher.releaseAll();

        assertFalse(dispatcher.isModifierLatched(PcKeyDispatcher.Modifier.CTRL));
        assertEquals(eventCountBeforeDisconnect, sender.events.size());
    }

    @Test
    public void notReadyProducesNoTranslationOrPackets() {
        FakeSender sender = new FakeSender();
        sender.ready = false;
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        assertFalse(dispatcher.tapKey(KeyEvent.KEYCODE_ESCAPE));
        assertFalse(dispatcher.dispatchShortcut(PcKeyDispatcher.PcShortcut.WIN_E));

        assertEquals(0, sender.baseReadCount);
        assertEquals(0, sender.translateCalls);
        assertTrue(sender.events.isEmpty());
    }

    @Test
    public void untranslatedMainKeyRejectsWholeChordBeforeSending() {
        FakeSender sender = new FakeSender();
        sender.untranslatableKeys.add(KeyEvent.KEYCODE_E);
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        assertFalse(dispatcher.dispatchShortcut(PcKeyDispatcher.PcShortcut.WIN_E));

        assertTrue(sender.events.isEmpty());
        dispatcher.releaseAll();
        assertTrue(sender.events.isEmpty());
    }

    @Test
    public void untranslatedOwnedModifierRejectsWholeChordBeforeSending() {
        FakeSender sender = new FakeSender();
        sender.untranslatableKeys.add(KeyEvent.KEYCODE_META_LEFT);
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        assertFalse(dispatcher.dispatchShortcut(PcKeyDispatcher.PcShortcut.WIN_E));

        assertTrue(sender.events.isEmpty());
    }

    @Test
    public void senderFailureUnwindsMainKeyAndOwnedModifier() {
        FakeSender sender = new FakeSender();
        sender.failOnceOnSendNumber = 1; // Fail while sending E down, after Meta down succeeded.
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        assertFalse(dispatcher.dispatchShortcut(PcKeyDispatcher.PcShortcut.WIN_E));

        assertEvents(sender,
                event(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_META),
                event(KeyEvent.KEYCODE_E, KeyboardPacket.KEY_DOWN,
                        KeyboardPacket.MODIFIER_META),
                event(KeyEvent.KEYCODE_E, KeyboardPacket.KEY_UP,
                        KeyboardPacket.MODIFIER_META),
                event(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_UP, 0));

        int eventCountAfterCleanup = sender.events.size();
        dispatcher.releaseAll();
        dispatcher.releaseAll();
        assertEquals(eventCountAfterCleanup, sender.events.size());
    }

    @Test
    public void oneHundredSequentialChordsDoNotInterleaveOrLeaveState() {
        FakeSender sender = new FakeSender();
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);

        for (int i = 0; i < 100; i++) {
            assertTrue(dispatcher.dispatchShortcut(PcKeyDispatcher.PcShortcut.WIN_E));
        }

        assertEquals(100, sender.baseReadCount);
        assertEquals(400, sender.events.size());
        for (int i = 0; i < 100; i++) {
            assertWinEBlock(sender.events, i * 4);
        }

        dispatcher.releaseAll();
        dispatcher.releaseAll();
        assertEquals(400, sender.events.size());
    }

    @Test
    public void concurrentRequestsRemainSerializedAsWholeChords() throws Exception {
        FakeSender sender = new FakeSender();
        PcKeyDispatcher dispatcher = new PcKeyDispatcher(sender);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<Boolean>> results = new ArrayList<>();

        try {
            for (int i = 0; i < 100; i++) {
                PcKeyDispatcher.PcShortcut shortcut = (i & 1) == 0
                        ? PcKeyDispatcher.PcShortcut.WIN_E
                        : PcKeyDispatcher.PcShortcut.PREVIOUS_WINDOW;
                results.add(executor.submit(() -> dispatcher.dispatchShortcut(shortcut)));
            }

            for (Future<Boolean> result : results) {
                assertTrue(result.get(5, TimeUnit.SECONDS));
            }
        }
        finally {
            executor.shutdownNow();
        }

        assertEquals(400, sender.events.size());
        int winEBlocks = 0;
        int altTabBlocks = 0;
        for (int offset = 0; offset < sender.events.size(); offset += 4) {
            Event first = sender.events.get(offset);
            if (first.keyMap == translated(KeyEvent.KEYCODE_META_LEFT)) {
                assertWinEBlock(sender.events, offset);
                winEBlocks++;
            }
            else {
                assertAltTabBlock(sender.events, offset);
                altTabBlocks++;
            }
        }
        assertEquals(50, winEBlocks);
        assertEquals(50, altTabBlocks);
    }

    private static void assertWinEBlock(List<Event> events, int offset) {
        assertEquals(event(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_DOWN,
                KeyboardPacket.MODIFIER_META), events.get(offset));
        assertEquals(event(KeyEvent.KEYCODE_E, KeyboardPacket.KEY_DOWN,
                KeyboardPacket.MODIFIER_META), events.get(offset + 1));
        assertEquals(event(KeyEvent.KEYCODE_E, KeyboardPacket.KEY_UP,
                KeyboardPacket.MODIFIER_META), events.get(offset + 2));
        assertEquals(event(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_UP, 0),
                events.get(offset + 3));
    }

    private static void assertAltTabBlock(List<Event> events, int offset) {
        assertEquals(event(KeyEvent.KEYCODE_ALT_LEFT, KeyboardPacket.KEY_DOWN,
                KeyboardPacket.MODIFIER_ALT), events.get(offset));
        assertEquals(event(KeyEvent.KEYCODE_TAB, KeyboardPacket.KEY_DOWN,
                KeyboardPacket.MODIFIER_ALT), events.get(offset + 1));
        assertEquals(event(KeyEvent.KEYCODE_TAB, KeyboardPacket.KEY_UP,
                KeyboardPacket.MODIFIER_ALT), events.get(offset + 2));
        assertEquals(event(KeyEvent.KEYCODE_ALT_LEFT, KeyboardPacket.KEY_UP, 0),
                events.get(offset + 3));
    }

    private static void assertMetaChord(List<Event> events, int offset, int mainKeyCode) {
        assertEquals(event(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_DOWN,
                KeyboardPacket.MODIFIER_META), events.get(offset));
        assertEquals(event(mainKeyCode, KeyboardPacket.KEY_DOWN,
                KeyboardPacket.MODIFIER_META), events.get(offset + 1));
        assertEquals(event(mainKeyCode, KeyboardPacket.KEY_UP,
                KeyboardPacket.MODIFIER_META), events.get(offset + 2));
        assertEquals(event(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_UP, 0),
                events.get(offset + 3));
    }

    private static void assertEvents(FakeSender sender, Event... expected) {
        assertEquals(listOf(expected), sender.events);
        assertAllFlagsNormalized(sender.events);
    }

    private static void assertAllFlagsNormalized(List<Event> events) {
        for (Event event : events) {
            assertEquals("All synthetic packets must use normalized flags", 0, event.flags);
        }
    }

    private static Event event(int androidKeyCode, byte direction, int modifiers) {
        return new Event(translated(androidKeyCode), direction, (byte) modifiers, (byte) 0);
    }

    private static short translated(int androidKeyCode) {
        // An intentionally fake mapping proves that PcKeyDispatcher never hard-codes Windows VKs.
        return (short) (0x4000 | androidKeyCode);
    }

    @SafeVarargs
    private static <T> List<T> listOf(T... values) {
        List<T> list = new ArrayList<>(values.length);
        for (T value : values) {
            list.add(value);
        }
        return list;
    }

    private static final class FakeSender implements PcKeyDispatcher.HostKeySender {
        private boolean ready = true;
        private byte baseModifiers;
        private int baseReadCount;
        private int translateCalls;
        private int sendCalls;
        private int failOnceOnSendNumber = -1;
        private boolean failureTriggered;
        private final Set<Integer> untranslatableKeys = new HashSet<>();
        private final Set<Integer> exactBaseKeys = new HashSet<>();
        private final List<Integer> translatedAndroidKeys = new ArrayList<>();
        private final List<Event> events = new ArrayList<>();

        @Override
        public boolean isReady() {
            return ready;
        }

        @Override
        public byte getBaseModifiers() {
            baseReadCount++;
            return baseModifiers;
        }

        @Override
        public boolean isBaseKeyDown(int androidKeyCode) {
            return exactBaseKeys.contains(androidKeyCode);
        }

        @Override
        public short translate(int androidKeyCode) {
            translateCalls++;
            translatedAndroidKeys.add(androidKeyCode);
            return untranslatableKeys.contains(androidKeyCode) ? 0 : translated(androidKeyCode);
        }

        @Override
        public void sendKeyboardInput(short keyMap, byte keyDirection, byte modifiers, byte flags) {
            events.add(new Event(keyMap, keyDirection, modifiers, flags));
            int thisSend = sendCalls++;
            if (!failureTriggered && thisSend == failOnceOnSendNumber) {
                failureTriggered = true;
                throw new IllegalStateException("Synthetic sender failure");
            }
        }
    }

    private static final class Event {
        private final short keyMap;
        private final byte direction;
        private final byte modifiers;
        private final byte flags;

        private Event(short keyMap, byte direction, byte modifiers, byte flags) {
            this.keyMap = keyMap;
            this.direction = direction;
            this.modifiers = modifiers;
            this.flags = flags;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Event)) {
                return false;
            }
            Event event = (Event) other;
            return keyMap == event.keyMap &&
                    direction == event.direction &&
                    modifiers == event.modifiers &&
                    flags == event.flags;
        }

        @Override
        public int hashCode() {
            int result = keyMap;
            result = 31 * result + direction;
            result = 31 * result + modifiers;
            result = 31 * result + flags;
            return result;
        }

        @Override
        public String toString() {
            return "Event{" +
                    "keyMap=0x" + Integer.toHexString(keyMap & 0xFFFF) +
                    ", direction=" + direction +
                    ", modifiers=" + modifiers +
                    ", flags=" + flags +
                    '}';
        }
    }
}
