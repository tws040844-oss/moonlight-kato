package com.limelight.binding.input;

import android.view.KeyEvent;

import com.limelight.nvstream.input.KeyboardPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends PC keys, latched modifiers, and fixed shortcuts without mutating Game's physical
 * modifier snapshot.
 *
 * <p>The dispatcher is deliberately independent of {@code NvConnection}. The activity supplies a
 * {@link HostKeySender} that delegates to the active connection, while unit tests can supply a fake
 * sender without loading MoonBridge's native library. Every public operation is synchronized so a
 * shortcut cannot interleave with another shortcut or a modifier transition.</p>
 */
public final class PcKeyDispatcher {
    private static final byte NORMALIZED_FLAGS = 0;
    private static final int ALL_MODIFIERS = KeyboardPacket.MODIFIER_SHIFT |
            KeyboardPacket.MODIFIER_CTRL |
            KeyboardPacket.MODIFIER_ALT |
            KeyboardPacket.MODIFIER_META;

    // The order here is also the temporary key-down order. Key-up is always the reverse order.
    private static final ModifierKey[] MODIFIER_KEYS = {
            new ModifierKey(KeyboardPacket.MODIFIER_CTRL, KeyEvent.KEYCODE_CTRL_LEFT),
            new ModifierKey(KeyboardPacket.MODIFIER_SHIFT, KeyEvent.KEYCODE_SHIFT_LEFT),
            new ModifierKey(KeyboardPacket.MODIFIER_ALT, KeyEvent.KEYCODE_ALT_LEFT),
            new ModifierKey(KeyboardPacket.MODIFIER_META, KeyEvent.KEYCODE_META_LEFT),
    };

    /** Modifiers that the v0.2 toolbar can keep held until the user taps them again. */
    public enum Modifier {
        CTRL(KeyboardPacket.MODIFIER_CTRL, KeyEvent.KEYCODE_CTRL_LEFT),
        SHIFT(KeyboardPacket.MODIFIER_SHIFT, KeyEvent.KEYCODE_SHIFT_LEFT);

        private final int mask;
        private final int androidKeyCode;

        Modifier(int mask, int androidKeyCode) {
            this.mask = mask;
            this.androidKeyCode = androidKeyCode;
        }
    }

    /** Fixed, atomic shortcuts exposed by the grouped v0.2 menu. */
    public enum PcShortcut {
        WIN_E(KeyEvent.KEYCODE_E, KeyboardPacket.MODIFIER_META),
        WIN_R(KeyEvent.KEYCODE_R, KeyboardPacket.MODIFIER_META),
        WIN_D(KeyEvent.KEYCODE_D, KeyboardPacket.MODIFIER_META),
        WIN_TAB(KeyEvent.KEYCODE_TAB, KeyboardPacket.MODIFIER_META),
        MAGNIFIER_ZOOM_IN(KeyEvent.KEYCODE_EQUALS, KeyboardPacket.MODIFIER_META),
        MAGNIFIER_ZOOM_OUT(KeyEvent.KEYCODE_MINUS, KeyboardPacket.MODIFIER_META),
        MAGNIFIER_CLOSE(KeyEvent.KEYCODE_ESCAPE, KeyboardPacket.MODIFIER_META),
        PREVIOUS_WINDOW(KeyEvent.KEYCODE_TAB, KeyboardPacket.MODIFIER_ALT),
        REVERSE_WINDOW(KeyEvent.KEYCODE_TAB,
                KeyboardPacket.MODIFIER_ALT | KeyboardPacket.MODIFIER_SHIFT),
        CTRL_A(KeyEvent.KEYCODE_A, KeyboardPacket.MODIFIER_CTRL),
        CTRL_C(KeyEvent.KEYCODE_C, KeyboardPacket.MODIFIER_CTRL),
        CTRL_X(KeyEvent.KEYCODE_X, KeyboardPacket.MODIFIER_CTRL),
        CTRL_V(KeyEvent.KEYCODE_V, KeyboardPacket.MODIFIER_CTRL),
        CTRL_Z(KeyEvent.KEYCODE_Z, KeyboardPacket.MODIFIER_CTRL),
        CTRL_Y(KeyEvent.KEYCODE_Y, KeyboardPacket.MODIFIER_CTRL),
        TASK_MANAGER(KeyEvent.KEYCODE_ESCAPE,
                KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT);

        private final int androidKeyCode;
        private final int requestedModifiers;

        PcShortcut(int androidKeyCode, int requestedModifiers) {
            this.androidKeyCode = androidKeyCode;
            this.requestedModifiers = requestedModifiers;
        }
    }

    /**
     * Adapter implemented by the streaming activity.
     *
     * <p>{@link #getBaseModifiers()} must return only the current physical/IME modifier snapshot.
     * Synthetic state owned by this dispatcher must never be written back into that snapshot.</p>
     */
    public interface HostKeySender {
        boolean isReady();

        byte getBaseModifiers();

        /** Returns whether this exact Android modifier key (including left/right side) is down. */
        boolean isBaseKeyDown(int androidKeyCode);

        short translate(int androidKeyCode);

        void sendKeyboardInput(short keyMap, byte keyDirection, byte modifiers, byte flags);
    }

    private static final class ModifierKey {
        private final int mask;
        private final int androidKeyCode;

        private ModifierKey(int mask, int androidKeyCode) {
            this.mask = mask;
            this.androidKeyCode = androidKeyCode;
        }
    }

    private static final class PressedKey {
        private final short keyMap;
        private final int modifierMask;

        private PressedKey(short keyMap, int modifierMask) {
            this.keyMap = keyMap;
            this.modifierMask = modifierMask;
        }
    }

    private final HostKeySender sender;
    private final List<PressedKey> transientPressedKeys = new ArrayList<>();
    private final List<PressedKey> latchedPressedKeys = new ArrayList<>();

    private int transientModifiers;
    private int latchedModifiers;
    private int activeBaseModifiers;

    public PcKeyDispatcher(HostKeySender sender) {
        if (sender == null) {
            throw new IllegalArgumentException("sender must not be null");
        }
        this.sender = sender;
    }

    /** Sends a single key tap while preserving any toolbar-latched Ctrl or Shift state. */
    public synchronized boolean tapKey(int androidKeyCode) {
        try {
            if (!sender.isReady()) {
                return false;
            }

            // Capture BASE exactly once for the whole tap.
            activeBaseModifiers = normalizeModifiers(sender.getBaseModifiers());

            int keyModifier = getModifierMask(androidKeyCode);
            if (keyModifier != 0 &&
                    ((activeBaseModifiers | latchedModifiers) & keyModifier) != 0) {
                // A standalone modifier tap is a no-op when that modifier is already held.
                return false;
            }

            short keyMap = sender.translate(androidKeyCode);
            if (keyMap == 0) {
                return false;
            }

            PressedKey key = new PressedKey(keyMap, keyModifier);
            pressTransient(key);
            releaseTransient(key);
            return true;
        }
        catch (RuntimeException ignored) {
            return false;
        }
        finally {
            // This also unwinds a partially sent tap without touching persistent latches.
            releaseTransientAllInternal();
        }
    }

    /** Sends one fixed shortcut as an atomic chord without releasing existing latches. */
    public synchronized boolean dispatchShortcut(PcShortcut shortcut) {
        if (shortcut == null) {
            return false;
        }

        try {
            if (!sender.isReady()) {
                return false;
            }

            activeBaseModifiers = normalizeModifiers(sender.getBaseModifiers());
            int requestedModifiers = shortcut.requestedModifiers & ALL_MODIFIERS;
            int alreadyHeldModifiers = activeBaseModifiers | latchedModifiers;
            int ownedModifiers = requestedModifiers & ~alreadyHeldModifiers;

            // Translate the complete chord before sending any packet.
            List<PressedKey> modifiers = new ArrayList<>(MODIFIER_KEYS.length);
            for (ModifierKey modifier : MODIFIER_KEYS) {
                if ((ownedModifiers & modifier.mask) != 0) {
                    short keyMap = sender.translate(modifier.androidKeyCode);
                    if (keyMap == 0) {
                        return false;
                    }
                    modifiers.add(new PressedKey(keyMap, modifier.mask));
                }
            }

            short mainKeyMap = sender.translate(shortcut.androidKeyCode);
            if (mainKeyMap == 0) {
                return false;
            }
            PressedKey mainKey = new PressedKey(mainKeyMap, 0);

            for (PressedKey modifier : modifiers) {
                pressTransient(modifier);
            }
            pressTransient(mainKey);
            releaseTransient(mainKey);
            for (int i = modifiers.size() - 1; i >= 0; i--) {
                releaseTransient(modifiers.get(i));
            }
            return true;
        }
        catch (RuntimeException ignored) {
            return false;
        }
        finally {
            releaseTransientAllInternal();
        }
    }

    /**
     * Toggles a persistent toolbar-owned modifier.
     *
     * <p>If the same physical modifier is already held when enabling, no synthetic ownership is
     * acquired. If a physical key becomes held after the latch and the user disables the latch,
     * ownership is transferred locally without sending KEY_UP; the later physical KEY_UP performs
     * the actual release.</p>
     */
    public synchronized boolean toggleModifier(Modifier modifier) {
        if (modifier == null) {
            return false;
        }

        if ((latchedModifiers & modifier.mask) != 0) {
            return unlatchModifier(modifier);
        }
        return latchModifier(modifier);
    }

    public synchronized boolean isModifierLatched(Modifier modifier) {
        return modifier != null && (latchedModifiers & modifier.mask) != 0;
    }

    /** Releases every toolbar-owned key. Safe to call repeatedly and after disconnection. */
    public synchronized void releaseAll() {
        releaseTransientAllInternal();

        if (latchedPressedKeys.isEmpty()) {
            clearLatchedState();
            return;
        }

        boolean ready = safeIsReady();
        activeBaseModifiers = ready ? safeReadBaseModifiers() : 0;
        try {
            for (int i = latchedPressedKeys.size() - 1; i >= 0; i--) {
                PressedKey key = latchedPressedKeys.get(i);
                latchedModifiers &= ~key.modifierMask;
                if (ready) {
                    try {
                        sender.sendKeyboardInput(key.keyMap, KeyboardPacket.KEY_UP,
                                currentModifiers(), NORMALIZED_FLAGS);
                    }
                    catch (RuntimeException ignored) {
                        // Keep releasing the remaining modifiers. Local state is always cleared.
                    }
                }
            }
        }
        finally {
            clearLatchedState();
            activeBaseModifiers = 0;
        }
    }

    private boolean latchModifier(Modifier modifier) {
        try {
            if (!sender.isReady()) {
                return false;
            }

            activeBaseModifiers = normalizeModifiers(sender.getBaseModifiers());
            if ((activeBaseModifiers & modifier.mask) != 0) {
                return false;
            }

            short keyMap = sender.translate(modifier.androidKeyCode);
            if (keyMap == 0) {
                return false;
            }

            PressedKey key = new PressedKey(keyMap, modifier.mask);
            latchedPressedKeys.add(key);
            latchedModifiers |= modifier.mask;
            try {
                sender.sendKeyboardInput(key.keyMap, KeyboardPacket.KEY_DOWN,
                        currentModifiers(), NORMALIZED_FLAGS);
                return true;
            }
            catch (RuntimeException ignored) {
                // The sender may have failed after dispatching. Best-effort a matching KEY_UP.
                latchedPressedKeys.remove(key);
                latchedModifiers &= ~modifier.mask;
                if (safeIsReady()) {
                    try {
                        sender.sendKeyboardInput(key.keyMap, KeyboardPacket.KEY_UP,
                                currentModifiers(), NORMALIZED_FLAGS);
                    }
                    catch (RuntimeException ignoredAgain) {
                        // Local ownership is already gone.
                    }
                }
                return false;
            }
        }
        catch (RuntimeException ignored) {
            return false;
        }
        finally {
            activeBaseModifiers = 0;
        }
    }

    private boolean unlatchModifier(Modifier modifier) {
        PressedKey key = findLatchedKey(modifier.mask);
        if (key == null) {
            latchedModifiers &= ~modifier.mask;
            return false;
        }

        boolean ready = safeIsReady();
        activeBaseModifiers = ready ? safeReadBaseModifiers() : 0;

        try {
            if (!ready) {
                // The session is already gone. Clear local ownership without claiming delivery.
                latchedPressedKeys.remove(key);
                latchedModifiers &= ~modifier.mask;
                return false;
            }

            if (safeIsExactBaseKeyDown(modifier.androidKeyCode)) {
                // This exact physical key has taken over. Its later KEY_UP performs the release.
                latchedPressedKeys.remove(key);
                latchedModifiers &= ~modifier.mask;
                return true;
            }

            // Keep local ownership until KEY_UP has been accepted by the sender. If sending throws,
            // the selected UI remains visible and a later tap or lifecycle cleanup can retry.
            byte modifiersAfterRelease = (byte) ((activeBaseModifiers |
                    (latchedModifiers & ~modifier.mask) | transientModifiers) & ALL_MODIFIERS);
            sender.sendKeyboardInput(key.keyMap, KeyboardPacket.KEY_UP,
                    modifiersAfterRelease, NORMALIZED_FLAGS);
            latchedPressedKeys.remove(key);
            latchedModifiers &= ~modifier.mask;
            return true;
        }
        catch (RuntimeException ignored) {
            return false;
        }
        finally {
            activeBaseModifiers = 0;
        }
    }

    private PressedKey findLatchedKey(int modifierMask) {
        for (PressedKey key : latchedPressedKeys) {
            if (key.modifierMask == modifierMask) {
                return key;
            }
        }
        return null;
    }

    private void pressTransient(PressedKey key) {
        // Record ownership before sending, because a sender can fail after partially dispatching.
        transientPressedKeys.add(key);
        transientModifiers |= key.modifierMask;
        sender.sendKeyboardInput(key.keyMap, KeyboardPacket.KEY_DOWN,
                currentModifiers(), NORMALIZED_FLAGS);
    }

    private void releaseTransient(PressedKey key) {
        int lastIndex = transientPressedKeys.size() - 1;
        if (lastIndex < 0 || transientPressedKeys.get(lastIndex) != key) {
            throw new IllegalStateException("Transient keys must be released in reverse order");
        }

        transientModifiers &= ~key.modifierMask;
        sender.sendKeyboardInput(key.keyMap, KeyboardPacket.KEY_UP,
                currentModifiers(), NORMALIZED_FLAGS);
        transientPressedKeys.remove(lastIndex);
    }

    private void releaseTransientAllInternal() {
        if (transientPressedKeys.isEmpty()) {
            transientModifiers = 0;
            activeBaseModifiers = 0;
            return;
        }

        boolean ready = safeIsReady();
        try {
            for (int i = transientPressedKeys.size() - 1; i >= 0; i--) {
                PressedKey key = transientPressedKeys.get(i);
                transientModifiers &= ~key.modifierMask;
                if (ready) {
                    try {
                        sender.sendKeyboardInput(key.keyMap, KeyboardPacket.KEY_UP,
                                currentModifiers(), NORMALIZED_FLAGS);
                    }
                    catch (RuntimeException ignored) {
                        // Continue unwinding. Local ownership is always cleared below.
                    }
                }
            }
        }
        finally {
            transientPressedKeys.clear();
            transientModifiers = 0;
            activeBaseModifiers = 0;
        }
    }

    private void clearLatchedState() {
        latchedPressedKeys.clear();
        latchedModifiers = 0;
    }

    private boolean safeIsReady() {
        try {
            return sender.isReady();
        }
        catch (RuntimeException ignored) {
            return false;
        }
    }

    private int safeReadBaseModifiers() {
        try {
            return normalizeModifiers(sender.getBaseModifiers());
        }
        catch (RuntimeException ignored) {
            return 0;
        }
    }

    private boolean safeIsExactBaseKeyDown(int androidKeyCode) {
        try {
            return sender.isBaseKeyDown(androidKeyCode);
        }
        catch (RuntimeException ignored) {
            return false;
        }
    }

    private byte currentModifiers() {
        return (byte) ((activeBaseModifiers | latchedModifiers | transientModifiers) & ALL_MODIFIERS);
    }

    private static int normalizeModifiers(byte modifiers) {
        return (modifiers & 0xFF) & ALL_MODIFIERS;
    }

    private static int getModifierMask(int androidKeyCode) {
        switch (androidKeyCode) {
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
                return KeyboardPacket.MODIFIER_SHIFT;
            case KeyEvent.KEYCODE_CTRL_LEFT:
            case KeyEvent.KEYCODE_CTRL_RIGHT:
                return KeyboardPacket.MODIFIER_CTRL;
            case KeyEvent.KEYCODE_ALT_LEFT:
            case KeyEvent.KEYCODE_ALT_RIGHT:
                return KeyboardPacket.MODIFIER_ALT;
            case KeyEvent.KEYCODE_META_LEFT:
            case KeyEvent.KEYCODE_META_RIGHT:
                return KeyboardPacket.MODIFIER_META;
            default:
                return 0;
        }
    }
}
