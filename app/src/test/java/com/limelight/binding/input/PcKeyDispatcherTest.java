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

    private static void assertEvents(FakeSender sender, Event... expected) {
        assertEquals(listOf(expected), sender.events);
        for (Event event : sender.events) {
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
