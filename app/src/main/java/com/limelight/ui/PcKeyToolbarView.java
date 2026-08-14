package com.limelight.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.DisplayCutout;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.binding.input.PcKeyDispatcher;

/**
 * Floating, tap-only PC key toolbar for the streaming activity.
 *
 * <p>This view deliberately owns only its visible bounds. It is intended to be added as a
 * {@code wrap_content} child at the end of {@code activity_game.xml}, with top/start gravity. The
 * view positions itself using {@link #setX(float)} and {@link #setY(float)} so it can dock against
 * either edge without adding a full-screen touch interceptor.</p>
 */
public final class PcKeyToolbarView extends FrameLayout {
    public static final String PREF_DOCK_SIDE = "pc_key_toolbar_dock_side";
    public static final String PREF_NORMALIZED_Y = "pc_key_toolbar_normalized_y";

    private static final String DOCK_LEFT = "left";
    private static final String DOCK_RIGHT = "right";
    private static final float DEFAULT_NORMALIZED_Y = 0.5f;

    /** Callbacks implemented by the streaming activity. */
    public interface Listener {
        void onToggleKeyboardRequested();

        void onKeyRequested(int androidKeyCode);

        void onShortcutRequested(PcKeyDispatcher.PcShortcut shortcut);

        default void onModifierToggleRequested(PcKeyDispatcher.Modifier modifier) {
        }

        void onSessionHideRequested();

        void onInputCancelled();
    }

    private enum DockSide {
        LEFT,
        RIGHT
    }

    private enum ShortcutGroup {
        WINDOWS,
        WINDOW,
        EDIT,
        SYSTEM
    }

    private final SharedPreferences preferences;
    private final int touchSlop;
    private final int edgeMarginPx;
    private final int toolbarElevationPx;
    private int safeInsetLeft;
    private int safeInsetTop;
    private int safeInsetRight;
    private int safeInsetBottom;

    private TextView collapsedButton;
    private View expandedPanel;
    private HorizontalScrollView primaryScroll;
    private View primaryRow;
    private TextView ctrlButton;
    private TextView shiftButton;
    private TextView shortcutsButton;
    private View shortcutPanel;
    private HorizontalScrollView groupScroll;
    private HorizontalScrollView windowsScroll;
    private HorizontalScrollView windowScroll;
    private HorizontalScrollView editScroll;
    private HorizontalScrollView systemScroll;
    private View groupRow;
    private View windowsRow;
    private View windowRow;
    private View editRow;
    private View systemRow;
    private TextView windowsGroupButton;
    private TextView windowGroupButton;
    private TextView editGroupButton;
    private TextView systemGroupButton;

    private Listener listener;
    private DockSide dockSide;
    private float normalizedY;
    private boolean expanded;
    private boolean sessionHidden;
    private boolean hostVisible = true;
    private boolean positionReady;
    private ShortcutGroup activeShortcutGroup = ShortcutGroup.WINDOWS;

    private float downRawX;
    private float downRawY;
    private float downViewX;
    private float downViewY;
    private boolean dragging;

    private View observedParent;
    private final OnLayoutChangeListener parentLayoutChangeListener =
            (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                    finishInitialPlacement();
                }
            };

    public PcKeyToolbarView(Context context) {
        this(context, null);
    }

    public PcKeyToolbarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PcKeyToolbarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        edgeMarginPx = dpToPx(8);
        toolbarElevationPx = dpToPx(16);

        initialize(context);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initialize(Context context) {
        setFocusable(false);
        setFocusableInTouchMode(false);
        setClickable(false);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setClipChildren(true);
        setClipToPadding(true);
        setElevation(toolbarElevationPx);

        LayoutInflater.from(context).inflate(R.layout.view_pc_key_toolbar, this, true);

        collapsedButton = findViewById(R.id.pc_key_collapsed_button);
        expandedPanel = findViewById(R.id.pc_key_expanded_panel);
        primaryScroll = findViewById(R.id.pc_key_primary_scroll);
        primaryRow = findViewById(R.id.pc_key_primary_row);
        ctrlButton = findViewById(R.id.pc_key_ctrl);
        shiftButton = findViewById(R.id.pc_key_shift);
        shortcutsButton = findViewById(R.id.pc_key_shortcuts);
        shortcutPanel = findViewById(R.id.pc_key_shortcut_panel);
        groupScroll = findViewById(R.id.pc_key_group_scroll);
        windowsScroll = findViewById(R.id.pc_key_windows_scroll);
        windowScroll = findViewById(R.id.pc_key_window_scroll);
        editScroll = findViewById(R.id.pc_key_edit_scroll);
        systemScroll = findViewById(R.id.pc_key_system_scroll);
        groupRow = findViewById(R.id.pc_key_group_row);
        windowsRow = findViewById(R.id.pc_key_windows_row);
        windowRow = findViewById(R.id.pc_key_window_row);
        editRow = findViewById(R.id.pc_key_edit_row);
        systemRow = findViewById(R.id.pc_key_system_row);
        windowsGroupButton = findViewById(R.id.pc_key_group_windows);
        windowGroupButton = findViewById(R.id.pc_key_group_window);
        editGroupButton = findViewById(R.id.pc_key_group_edit);
        systemGroupButton = findViewById(R.id.pc_key_group_system);

        dockSide = DOCK_LEFT.equals(preferences.getString(PREF_DOCK_SIDE, DOCK_RIGHT))
                ? DockSide.LEFT : DockSide.RIGHT;
        normalizedY = clamp01(preferences.getFloat(PREF_NORMALIZED_Y, DEFAULT_NORMALIZED_Y));

        configureLabels();
        configureButtons();
        setModifierLatched(false, false);
        showShortcutGroup(ShortcutGroup.WINDOWS);
        makeTreeNonFocusable(this);

        collapsedButton.setOnTouchListener(this::handleCollapsedButtonTouch);
        collapse();

        // Avoid a single-frame flash in the top-left corner before the parent has dimensions.
        positionReady = false;
        applyRequestedVisibility();
    }

    private void configureButtons() {
        collapsedButton.setOnClickListener(view -> expand());

        findViewById(R.id.pc_key_keyboard).setOnClickListener(view -> {
            if (listener != null) {
                listener.onToggleKeyboardRequested();
            }
        });

        bindKeyButton(R.id.pc_key_win, KeyEvent.KEYCODE_META_LEFT);
        bindModifierButton(R.id.pc_key_ctrl, PcKeyDispatcher.Modifier.CTRL);
        bindModifierButton(R.id.pc_key_shift, PcKeyDispatcher.Modifier.SHIFT);
        bindKeyButton(R.id.pc_key_caps, KeyEvent.KEYCODE_CAPS_LOCK);
        bindKeyButton(R.id.pc_key_escape, KeyEvent.KEYCODE_ESCAPE);
        bindKeyButton(R.id.pc_key_tab, KeyEvent.KEYCODE_TAB);
        bindKeyButton(R.id.pc_key_left, KeyEvent.KEYCODE_DPAD_LEFT);
        bindKeyButton(R.id.pc_key_up, KeyEvent.KEYCODE_DPAD_UP);
        bindKeyButton(R.id.pc_key_down, KeyEvent.KEYCODE_DPAD_DOWN);
        bindKeyButton(R.id.pc_key_right, KeyEvent.KEYCODE_DPAD_RIGHT);

        shortcutsButton.setOnClickListener(view -> toggleShortcutPanel());
        findViewById(R.id.pc_key_collapse).setOnClickListener(view -> collapse());

        windowsGroupButton.setOnClickListener(view -> showShortcutGroup(ShortcutGroup.WINDOWS));
        windowGroupButton.setOnClickListener(view -> showShortcutGroup(ShortcutGroup.WINDOW));
        editGroupButton.setOnClickListener(view -> showShortcutGroup(ShortcutGroup.EDIT));
        systemGroupButton.setOnClickListener(view -> showShortcutGroup(ShortcutGroup.SYSTEM));

        bindShortcutButton(R.id.pc_key_shortcut_win_e, PcKeyDispatcher.PcShortcut.WIN_E);
        bindShortcutButton(R.id.pc_key_shortcut_win_r, PcKeyDispatcher.PcShortcut.WIN_R);
        bindShortcutButton(R.id.pc_key_shortcut_win_d, PcKeyDispatcher.PcShortcut.WIN_D);
        bindShortcutButton(R.id.pc_key_shortcut_win_tab, PcKeyDispatcher.PcShortcut.WIN_TAB);
        bindShortcutButton(R.id.pc_key_shortcut_magnifier_zoom_in,
                PcKeyDispatcher.PcShortcut.MAGNIFIER_ZOOM_IN);
        bindShortcutButton(R.id.pc_key_shortcut_magnifier_zoom_out,
                PcKeyDispatcher.PcShortcut.MAGNIFIER_ZOOM_OUT);
        bindShortcutButton(R.id.pc_key_shortcut_magnifier_close,
                PcKeyDispatcher.PcShortcut.MAGNIFIER_CLOSE);
        bindShortcutButton(R.id.pc_key_shortcut_previous_window,
                PcKeyDispatcher.PcShortcut.PREVIOUS_WINDOW);
        bindShortcutButton(R.id.pc_key_shortcut_shift_tab,
                PcKeyDispatcher.PcShortcut.REVERSE_WINDOW);
        bindShortcutButton(R.id.pc_key_shortcut_ctrl_a, PcKeyDispatcher.PcShortcut.CTRL_A);
        bindShortcutButton(R.id.pc_key_shortcut_ctrl_c, PcKeyDispatcher.PcShortcut.CTRL_C);
        bindShortcutButton(R.id.pc_key_shortcut_ctrl_x, PcKeyDispatcher.PcShortcut.CTRL_X);
        bindShortcutButton(R.id.pc_key_shortcut_ctrl_v, PcKeyDispatcher.PcShortcut.CTRL_V);
        bindShortcutButton(R.id.pc_key_shortcut_ctrl_z, PcKeyDispatcher.PcShortcut.CTRL_Z);
        bindShortcutButton(R.id.pc_key_shortcut_ctrl_y, PcKeyDispatcher.PcShortcut.CTRL_Y);
        bindShortcutButton(R.id.pc_key_shortcut_task_manager,
                PcKeyDispatcher.PcShortcut.TASK_MANAGER);

        findViewById(R.id.pc_key_hide_session).setOnClickListener(view -> {
            hideForSession();
            if (listener != null) {
                listener.onSessionHideRequested();
            }
        });
    }

    private void bindKeyButton(int viewId, int androidKeyCode) {
        findViewById(viewId).setOnClickListener(view -> {
            if (listener != null) {
                listener.onKeyRequested(androidKeyCode);
            }
        });
    }

    private void bindModifierButton(int viewId, PcKeyDispatcher.Modifier modifier) {
        findViewById(viewId).setOnClickListener(view -> {
            if (listener != null) {
                listener.onModifierToggleRequested(modifier);
            }
        });
    }

    private void bindShortcutButton(int viewId, PcKeyDispatcher.PcShortcut shortcut) {
        findViewById(viewId).setOnClickListener(view -> {
            if (listener != null) {
                listener.onShortcutRequested(shortcut);
            }
        });
    }

    /** Supplies callbacks for UI actions. Passing {@code null} safely disables dispatch. */
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Updates the visual and accessibility state of the two latched modifiers. */
    public void setModifierLatched(boolean ctrlLatched, boolean shiftLatched) {
        setModifierButtonState(ctrlButton, ctrlLatched,
                R.string.pc_key_toolbar_ctrl_description,
                R.string.pc_key_toolbar_ctrl_description_on);
        setModifierButtonState(shiftButton, shiftLatched,
                R.string.pc_key_toolbar_shift_description,
                R.string.pc_key_toolbar_shift_description_on);
    }

    private void setModifierButtonState(TextView button, boolean latched,
                                        int inactiveDescription, int activeDescription) {
        if (button == null) {
            return;
        }

        button.setActivated(latched);
        button.setSelected(latched);
        button.setContentDescription(getResources().getText(
                latched ? activeDescription : inactiveDescription));
    }

    /** Resets transient state for a new stream while preserving the user's saved dock position. */
    public void resetForSession() {
        sessionHidden = false;
        collapse();
        applyRequestedVisibility();
        finishInitialPlacement();
        ensureOnTop();
    }

    /** Restores the default right/center dock position and persists it. */
    public void resetDockPosition() {
        dockSide = DockSide.RIGHT;
        normalizedY = DEFAULT_NORMALIZED_Y;
        persistDockPosition();
        reposition();
    }

    /** Hides this toolbar until {@link #resetForSession()} is called. */
    public void hideForSession() {
        sessionHidden = true;
        collapse();
        applyRequestedVisibility();
    }

    /**
     * Controls temporary host visibility for PiP, disconnection, or a disabled preference.
     * Session-hidden state is kept separately and remains authoritative.
     */
    public void setHostVisible(boolean visible) {
        hostVisible = visible;
        applyRequestedVisibility();
        if (visible) {
            finishInitialPlacement();
            ensureOnTop();
        }
    }

    public boolean isHiddenForSession() {
        return sessionHidden;
    }

    public boolean isExpanded() {
        return expanded;
    }

    /** Expands the primary key row and keeps the fixed core keys scrolled to the front. */
    public void expand() {
        if (sessionHidden || !hostVisible) {
            return;
        }

        expanded = true;
        collapsedButton.setVisibility(GONE);
        expandedPanel.setVisibility(VISIBLE);
        shortcutPanel.setVisibility(GONE);
        setGroupButtonActive(shortcutsButton, false);
        resizeAndReposition();
        primaryScroll.post(() -> primaryScroll.scrollTo(0, 0));
        ensureOnTop();
    }

    /** Collapses the toolbar to the 48dp PC button. */
    public void collapse() {
        expanded = false;
        if (collapsedButton != null) {
            collapsedButton.setVisibility(VISIBLE);
        }
        if (expandedPanel != null) {
            expandedPanel.setVisibility(GONE);
        }
        if (shortcutPanel != null) {
            shortcutPanel.setVisibility(GONE);
        }
        if (shortcutsButton != null) {
            setGroupButtonActive(shortcutsButton, false);
        }
        resizeAndReposition();
    }

    /** Re-clamps the toolbar after rotation, folding, IME changes, or parent-size changes. */
    public void reposition() {
        View parent = getViewParent();
        if (parent == null || parent.getWidth() == 0 || parent.getHeight() == 0 ||
                getWidth() == 0 || getHeight() == 0) {
            return;
        }

        updateSafeInsets();

        float left = getSafeLeft(parent.getWidth());
        float right = getSafeRight(parent.getWidth());
        float top = getSafeTop(parent.getHeight());
        float bottom = getSafeBottom(parent.getHeight());

        setX(dockSide == DockSide.LEFT ? left : right);
        setY(top + ((bottom - top) * clamp01(normalizedY)));
    }

    /** Keeps the toolbar above virtual-controller elements dynamically added to the activity. */
    public void ensureOnTop() {
        bringToFront();
        setElevation(toolbarElevationPx);
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        observeParentSize();
        post(this::finishInitialPlacement);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (observedParent != null) {
            observedParent.removeOnLayoutChangeListener(parentLayoutChangeListener);
            observedParent = null;
        }
        positionReady = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width != oldWidth || height != oldHeight) {
            post(this::reposition);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL && listener != null) {
            listener.onInputCancelled();
        }
        return super.dispatchTouchEvent(event);
    }

    private boolean handleCollapsedButtonTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                downViewX = getX();
                downViewY = getY();
                dragging = false;
                view.setPressed(true);
                return true;

            case MotionEvent.ACTION_MOVE:
                float deltaX = event.getRawX() - downRawX;
                float deltaY = event.getRawY() - downRawY;
                if (!dragging && ((deltaX * deltaX) + (deltaY * deltaY)) >
                        (touchSlop * touchSlop)) {
                    dragging = true;
                    view.setPressed(false);
                    requestParentIntercept(false);
                }

                if (dragging) {
                    moveWithinParent(downViewX + deltaX, downViewY + deltaY);
                }
                return true;

            case MotionEvent.ACTION_UP:
                view.setPressed(false);
                requestParentIntercept(true);
                if (dragging) {
                    dockToNearestSideAndPersist();
                }
                else {
                    view.performClick();
                }
                dragging = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                view.setPressed(false);
                requestParentIntercept(true);
                if (dragging) {
                    dockToNearestSideAndPersist();
                }
                else {
                    reposition();
                }
                dragging = false;
                return true;

            default:
                return true;
        }
    }

    private void requestParentIntercept(boolean allowIntercept) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(!allowIntercept);
        }
    }

    private void moveWithinParent(float desiredX, float desiredY) {
        View parent = getViewParent();
        if (parent == null) {
            return;
        }

        updateSafeInsets();

        setX(clamp(desiredX, getSafeLeft(parent.getWidth()), getSafeRight(parent.getWidth())));
        setY(clamp(desiredY, getSafeTop(parent.getHeight()), getSafeBottom(parent.getHeight())));
    }

    private void dockToNearestSideAndPersist() {
        View parent = getViewParent();
        if (parent == null || parent.getWidth() == 0 || parent.getHeight() == 0) {
            return;
        }

        dockSide = getX() + (getWidth() / 2f) < parent.getWidth() / 2f
                ? DockSide.LEFT : DockSide.RIGHT;

        float top = getSafeTop(parent.getHeight());
        float bottom = getSafeBottom(parent.getHeight());
        float travel = bottom - top;
        normalizedY = travel > 0f ? clamp01((getY() - top) / travel) : DEFAULT_NORMALIZED_Y;

        persistDockPosition();
        reposition();
    }

    private void persistDockPosition() {
        preferences.edit()
                .putString(PREF_DOCK_SIDE, dockSide == DockSide.LEFT ? DOCK_LEFT : DOCK_RIGHT)
                .putFloat(PREF_NORMALIZED_Y, clamp01(normalizedY))
                .apply();
    }

    private void toggleShortcutPanel() {
        boolean show = shortcutPanel.getVisibility() != VISIBLE;
        shortcutPanel.setVisibility(show ? VISIBLE : GONE);
        setGroupButtonActive(shortcutsButton, show);
        if (show) {
            showShortcutGroup(activeShortcutGroup);
            groupScroll.post(() -> groupScroll.scrollTo(0, 0));
        }
        else {
            resizeAndReposition();
        }
    }

    private void showShortcutGroup(ShortcutGroup group) {
        activeShortcutGroup = group;

        windowsScroll.setVisibility(group == ShortcutGroup.WINDOWS ? VISIBLE : GONE);
        windowScroll.setVisibility(group == ShortcutGroup.WINDOW ? VISIBLE : GONE);
        editScroll.setVisibility(group == ShortcutGroup.EDIT ? VISIBLE : GONE);
        systemScroll.setVisibility(group == ShortcutGroup.SYSTEM ? VISIBLE : GONE);

        setGroupButtonActive(windowsGroupButton, group == ShortcutGroup.WINDOWS);
        setGroupButtonActive(windowGroupButton, group == ShortcutGroup.WINDOW);
        setGroupButtonActive(editGroupButton, group == ShortcutGroup.EDIT);
        setGroupButtonActive(systemGroupButton, group == ShortcutGroup.SYSTEM);

        resizeAndReposition();
        HorizontalScrollView activeScroll = getActiveShortcutScroll();
        activeScroll.post(() -> activeScroll.scrollTo(0, 0));
    }

    private static void setGroupButtonActive(TextView button, boolean active) {
        button.setActivated(active);
        button.setSelected(active);
    }

    private HorizontalScrollView getActiveShortcutScroll() {
        switch (activeShortcutGroup) {
            case WINDOW:
                return windowScroll;
            case EDIT:
                return editScroll;
            case SYSTEM:
                return systemScroll;
            case WINDOWS:
            default:
                return windowsScroll;
        }
    }

    private void resizeAndReposition() {
        if (expandedPanel == null) {
            return;
        }

        updateExpandedPanelWidth();
        requestLayout();
        post(this::reposition);
    }

    private void finishInitialPlacement() {
        observeParentSize();
        View parent = getViewParent();
        if (parent == null || parent.getWidth() == 0 || parent.getHeight() == 0) {
            return;
        }

        updateExpandedPanelWidth();
        positionReady = true;
        applyRequestedVisibility();
        post(this::reposition);
    }

    private void observeParentSize() {
        View newParent = getViewParent();
        if (newParent == observedParent) {
            return;
        }

        if (observedParent != null) {
            observedParent.removeOnLayoutChangeListener(parentLayoutChangeListener);
        }
        observedParent = newParent;
        if (observedParent != null) {
            observedParent.addOnLayoutChangeListener(parentLayoutChangeListener);
        }
    }

    private void applyRequestedVisibility() {
        if (!hostVisible || sessionHidden) {
            setVisibility(GONE);
        }
        else if (!positionReady) {
            setVisibility(INVISIBLE);
        }
        else {
            setVisibility(VISIBLE);
        }
    }

    private void updateExpandedPanelWidth() {
        View parent = getViewParent();
        if (parent == null || parent.getWidth() == 0 || primaryRow == null) {
            return;
        }

        int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        primaryRow.measure(unspecified, unspecified);
        int naturalWidth = primaryRow.getMeasuredWidth();
        if (shortcutPanel.getVisibility() == VISIBLE) {
            groupRow.measure(unspecified, unspecified);
            View activeRow = getActiveShortcutRow();
            activeRow.measure(unspecified, unspecified);
            naturalWidth = Math.max(naturalWidth,
                    Math.max(groupRow.getMeasuredWidth(), activeRow.getMeasuredWidth()));
        }
        naturalWidth += expandedPanel.getPaddingLeft() + expandedPanel.getPaddingRight();

        updateSafeInsets();
        int maximumWidth = Math.max(1, parent.getWidth() - safeInsetLeft - safeInsetRight -
                (edgeMarginPx * 2));
        int desiredWidth = Math.min(naturalWidth, maximumWidth);
        ViewGroup.LayoutParams params = expandedPanel.getLayoutParams();
        if (params.width != desiredWidth) {
            params.width = desiredWidth;
            expandedPanel.setLayoutParams(params);
        }
    }

    private View getActiveShortcutRow() {
        switch (activeShortcutGroup) {
            case WINDOW:
                return windowRow;
            case EDIT:
                return editRow;
            case SYSTEM:
                return systemRow;
            case WINDOWS:
            default:
                return windowsRow;
        }
    }

    private View getViewParent() {
        ViewParent parent = getParent();
        return parent instanceof View ? (View) parent : null;
    }

    private float getSafeLeft(int parentWidth) {
        int maxX = Math.max(0, parentWidth - getWidth());
        return clamp(safeInsetLeft + edgeMarginPx, 0f, maxX);
    }

    private float getSafeRight(int parentWidth) {
        int maxX = Math.max(0, parentWidth - getWidth());
        return Math.max(getSafeLeft(parentWidth), maxX - safeInsetRight - edgeMarginPx);
    }

    private float getSafeTop(int parentHeight) {
        int maxY = Math.max(0, parentHeight - getHeight());
        return clamp(safeInsetTop + edgeMarginPx, 0f, maxY);
    }

    private float getSafeBottom(int parentHeight) {
        int maxY = Math.max(0, parentHeight - getHeight());
        return Math.max(getSafeTop(parentHeight), maxY - safeInsetBottom - edgeMarginPx);
    }

    @SuppressWarnings("deprecation")
    private void updateSafeInsets() {
        int left = 0;
        int top = 0;
        int right = 0;
        int bottom = 0;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            WindowInsets windowInsets = getRootWindowInsets();
            if (windowInsets != null) {
                left = Math.max(left, windowInsets.getSystemWindowInsetLeft());
                top = Math.max(top, windowInsets.getSystemWindowInsetTop());
                right = Math.max(right, windowInsets.getSystemWindowInsetRight());
                bottom = Math.max(bottom, windowInsets.getSystemWindowInsetBottom());

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    DisplayCutout cutout = windowInsets.getDisplayCutout();
                    if (cutout != null) {
                        left = Math.max(left, cutout.getSafeInsetLeft());
                        top = Math.max(top, cutout.getSafeInsetTop());
                        right = Math.max(right, cutout.getSafeInsetRight());
                        bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Insets gestures = windowInsets.getMandatorySystemGestureInsets();
                    left = Math.max(left, gestures.left);
                    top = Math.max(top, gestures.top);
                    right = Math.max(right, gestures.right);
                    bottom = Math.max(bottom, gestures.bottom);
                }
            }
        }

        safeInsetLeft = left;
        safeInsetTop = top;
        safeInsetRight = right;
        safeInsetBottom = bottom;
    }

    private void configureLabels() {
        configureLabel(R.id.pc_key_collapsed_button,
                R.string.pc_key_toolbar_pc, R.string.pc_key_toolbar_pc_description);
        configureLabel(R.id.pc_key_keyboard,
                R.string.pc_key_toolbar_keyboard, R.string.pc_key_toolbar_keyboard_description);
        configureLabel(R.id.pc_key_win,
                R.string.pc_key_toolbar_win, R.string.pc_key_toolbar_win_description);
        configureLabel(R.id.pc_key_escape,
                R.string.pc_key_toolbar_escape, R.string.pc_key_toolbar_escape_description);
        configureLabel(R.id.pc_key_tab,
                R.string.pc_key_toolbar_tab, R.string.pc_key_toolbar_tab_description);
        configureLabel(R.id.pc_key_left,
                R.string.pc_key_toolbar_left, R.string.pc_key_toolbar_left_description);
        configureLabel(R.id.pc_key_up,
                R.string.pc_key_toolbar_up, R.string.pc_key_toolbar_up_description);
        configureLabel(R.id.pc_key_down,
                R.string.pc_key_toolbar_down, R.string.pc_key_toolbar_down_description);
        configureLabel(R.id.pc_key_right,
                R.string.pc_key_toolbar_right, R.string.pc_key_toolbar_right_description);
        configureLabel(R.id.pc_key_shortcuts,
                R.string.pc_key_toolbar_shortcuts, R.string.pc_key_toolbar_shortcuts_description);
        configureLabel(R.id.pc_key_collapse,
                R.string.pc_key_toolbar_collapse, R.string.pc_key_toolbar_collapse_description);
        configureLabel(R.id.pc_key_shortcut_win_e,
                R.string.pc_key_toolbar_shortcut_win_e,
                R.string.pc_key_toolbar_shortcut_win_e_description);
        configureLabel(R.id.pc_key_shortcut_win_r,
                R.string.pc_key_toolbar_shortcut_win_r,
                R.string.pc_key_toolbar_shortcut_win_r_description);
        configureLabel(R.id.pc_key_shortcut_previous_window,
                R.string.pc_key_toolbar_shortcut_previous_window,
                R.string.pc_key_toolbar_shortcut_previous_window_description);
        configureLabel(R.id.pc_key_shortcut_task_manager,
                R.string.pc_key_toolbar_shortcut_task_manager,
                R.string.pc_key_toolbar_shortcut_task_manager_description);
        configureLabel(R.id.pc_key_hide_session,
                R.string.pc_key_toolbar_hide_session,
                R.string.pc_key_toolbar_hide_session_description);
    }

    private void configureLabel(int viewId, int textResourceId, int descriptionResourceId) {
        TextView view = findViewById(viewId);
        view.setText(textResourceId);
        view.setContentDescription(getResources().getText(descriptionResourceId));
    }

    private static void makeTreeNonFocusable(View view) {
        view.setFocusable(false);
        view.setFocusableInTouchMode(false);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                makeTreeNonFocusable(group.getChildAt(i));
            }
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return DEFAULT_NORMALIZED_Y;
        }
        return clamp(value, 0f, 1f);
    }
}
