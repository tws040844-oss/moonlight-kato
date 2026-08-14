package com.limelight.binding.input;

import android.view.KeyEvent;

import com.limelight.nvstream.input.KeyboardPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends tap-only PC keys and shortcuts without mutating the physical keyboard modifier state.
 *
 * <p>The dispatcher is deliberately independent of {@code NvConnection}. The activity supplies a
 * {@link HostKeySender} that delegates to the active connection, while unit tests can supply a fake
 * sender without loading MoonBridge's native library.</p>
 */
public final class PcKeyDispatcher {
    private static final byte NORMALIZED_FLAGS = 0;
    private static final int ALL_MODIFIERS = KeyboardPacket.MODIFIER_SHIFT |
            KeyboardPacket.MODIFIER_CTRL |
            KeyboardPacket.MODIFIER_ALT |
            KeyboardPacket.MODIFIER_META;

    // The order here is also the key-down order. Key-up is always the reverse order.
    private static final ModifierKey[] MODIFIER_KEYS = {
            new ModifierKey(KeyboardPacket.MODIFIER_CTRL, KeyEvent.KEYCODE_CTRL_LEFT),
            new ModifierKey(KeyboardPacket.MODIFIER_SHIFT, KeyEvent.KEYCODE_SHIFT_LEFT),
            new ModifierKey(KeyboardPacket.MODIFIER_ALT, KeyEvent.KEYCODE_ALT_LEFT),
            new ModifierKey(KeyboardPacket.MODIFIER_META, KeyEvent.KEYCODE_META_LEFT),
    };

    /** The fixed set of shortcuts exposed by the v0.1 toolbar. */
    public enum PcShortcut {
        WIN_E(KeyEvent.KEYCODE_E, KeyboardPacket.MODIFIER_META),
        WIN_R(KeyEvent.KEYCODE_R, KeyboardPacket.MODIFIER_META),
        PREVIOUS_WINDOW(KeyEvent.KEYCODE_TAB, KeyboardPacket.MODIFIER_ALT),
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
    private final List<PressedKey> pressedKeys = new ArrayList<>();

    private int syntheticModifiers;
    private int activeBaseModifiers;

    public PcKeyDispatcher(HostKeySender sender) {
        if (sender == null) {
            throw new IllegalArgumentException("sender must not be null");
        }
        this.sender = sender;
    }

    /**
     * Sends a single key tap. Modifier keys are sent with their modifier bit set on key-down and
     * cleared on key-up, matching Moonlight's physical-key path.
     *
     * @return {@code true} when the complete tap was sent, otherwise {@code false}
     */
    public synchronized boolean tapKey(int androidKeyCode) {
        try {
            if (!sender.isReady()) {
                return false;
            }

            // Capture BASE exactly once for the whole tap.
            activeBaseModifiers = normalizeModifiers(sender.getBaseModifiers());

            int keyModifier = getModifierMask(androidKeyCode);
            if (keyModifier != 0 && (activeBaseModifiers & keyModifier) != 0) {
                // In particular, a standalone Win tap while physical Meta is held is a no-op.
                return false;
            }

            short keyMap = sender.translate(androidKeyCode);
            if (keyMap == 0) {
                return false;
            }

            PressedKey key = new PressedKey(keyMap, keyModifier);
            press(key);
            release(key);
            return true;
        }
        catch (RuntimeException ignored) {
            return false;
        }
        finally {
            // This is normally already empty. It also unwinds partial sends after sender failure.
            releaseAllInternal();
        }
    }

    /**
     * Sends one of the fixed v0.1 shortcuts as an atomic, synchronized chord.
     *
     * @return {@code true} when the complete chord was sent, otherwise {@code false}
     */
    public synchronized boolean dispatchShortcut(PcShortcut shortcut) {
        if (shortcut == null) {
            return false;
        }

        try {
            if (!sender.isReady()) {
                return false;
            }

            // Capture BASE exactly once. Modifiers already held by the user are not owned by us.
            activeBaseModifiers = normalizeModifiers(sender.getBaseModifiers());
            int requestedModifiers = shortcut.requestedModifiers & ALL_MODIFIERS;
            int ownedModifiers = requestedModifiers & ~activeBaseModifiers;

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
                press(modifier);
            }
            press(mainKey);
            release(mainKey);
            for (int i = modifiers.size() - 1; i >= 0; i--) {
                release(modifiers.get(i));
            }
            return true;
        }
        catch (RuntimeException ignored) {
            return false;
        }
        finally {
            releaseAllInternal();
        }
    }

    /** Releases any synthetic keys still owned by this dispatcher. Safe to call repeatedly. */
    public synchronized void releaseAll() {
        releaseAllInternal();
    }

    private void press(PressedKey key) {
        // Record ownership before sending, because a sender can fail after partially dispatching.
        pressedKeys.add(key);
        syntheticModifiers |= key.modifierMask;
        sender.sendKeyboardInput(key.keyMap, KeyboardPacket.KEY_DOWN,
                currentModifiers(), NORMALIZED_FLAGS);
    }

    private void release(PressedKey key) {
        int lastIndex = pressedKeys.size() - 1;
        if (lastIndex < 0 || pressedKeys.get(lastIndex) != key) {
            throw new IllegalStateException("Synthetic keys must be released in reverse order");
        }

        // Modifier key-up packets carry the state after that modifier has been released.
        syntheticModifiers &= ~key.modifierMask;
        sender.sendKeyboardInput(key.keyMap, KeyboardPacket.KEY_UP,
                currentModifiers(), NORMALIZED_FLAGS);
        pressedKeys.remove(lastIndex);
    }

    private void releaseAllInternal() {
        if (pressedKeys.isEmpty()) {
            clearLocalState();
            return;
        }

        boolean ready;
        try {
            ready = sender.isReady();
        }
        catch (RuntimeException ignored) {
            ready = false;
        }

        try {
            for (int i = pressedKeys.size() - 1; i >= 0; i--) {
                PressedKey key = pressedKeys.get(i);
                syntheticModifiers &= ~key.modifierMask;

                if (ready) {
                    try {
                        sender.sendKeyboardInput(key.keyMap, KeyboardPacket.KEY_UP,
                                currentModifiers(), NORMALIZED_FLAGS);
                    }
                    catch (RuntimeException ignored) {
                        // Continue releasing the remaining keys. Local ownership is always cleared.
                    }
                }
            }
        }
        finally {
            clearLocalState();
        }
    }

    private void clearLocalState() {
        pressedKeys.clear();
        syntheticModifiers = 0;
        activeBaseModifiers = 0;
    }

    private byte currentModifiers() {
        return (byte) ((activeBaseModifiers | syntheticModifiers) & ALL_MODIFIERS);
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
